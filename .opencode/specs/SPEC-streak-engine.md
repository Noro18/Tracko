# Spec: `streak-engine` (Module 2 of 8)

**Status:** ACCEPTED — approved by human 2026-10-11 (approved without full review; tutorial pace agreed for build).
**Author:** opencode | **Date:** 2026-10-11
**Parent:** `docs/architecture.md` v1.1 (approved) → capability map module `streak-engine`
**Implements:** behavior half of data model v1.1 (§3, §4.5–§4.8, §4.12–§4.13); no UI yet
**Depends on:** `data-core` (ACCEPTED 2026-10-11) — entities, DAOs, repositories, converters

---

## Objective

Give the app its brain: four pure-Kotlin pieces that turn rows into meaning — streaks, finalization, weekly freezes, and logging guards. Everything here runs on the plain JVM (NFR-6): **zero `android.*`/`androidx.*` imports, `today` passed in as a parameter** (data model §4.13). No screens, no ViewModels, no workers — those come in later modules and only *call* this engine.

**Done means:** the 12 scenarios from data model §7 pass as JVM unit tests plus their edge variants, `grep -r "android\." app/src/main/java/com/example/tracko/domain/` returns nothing, the two `TODO`s deferred from `data-core` (`resume` backfill, `editActiveDays` body) are resolved, and `./gradlew test` + `./gradlew lint` + `assembleDebug` are green.

## Tech Stack (changes)

None. This slice adds no dependencies — JUnit4 + `kotlinx-coroutines-test` (already in `libs.versions.toml` from `data-core` task 1) cover everything, because the engine is synchronous pure logic with no coroutines of its own. If a test later needs `StandardTestDispatcher`, it is already available.

## Project Structure (files created / modified)

```
app/src/main/java/com/example/tracko/
├── domain/
│   ├── ActiveDays.kt                # NEW: maskInForceOn + isActiveOn helpers (§4.12)
│   ├── StreakCalculator.kt          # NEW: per-habit + overall daily streaks (§3)
│   ├── Finalizer.kt                 # NEW: finalization routine (§4.6) + resume/edit support (§4.8, §4.12)
│   ├── WeeklyFreezeJudge.kt         # NEW: weekly freeze judging (§4.5, §4.7)
│   └── LoggingRules.kt              # NEW: logging window + BONUS (§4.13, FR-31)
├── data/repository/
│   ├── HabitRepository.kt           # MODIFY: resolve resume/editActiveDays TODOs via Finalizer
│   └── HabitLogRepository.kt        # MODIFY: upsert validates via LoggingRules (still the only write path)

app/src/test/java/com/example/tracko/
├── domain/
│   ├── StreakCalculatorTest.kt      # NEW: §7 scenarios 1, 2, 8, 9 + edge variants
│   ├── FinalizerTest.kt             # NEW: §7 scenarios 3, 4, 5, 6, 10, 11 + edge variants
│   ├── WeeklyFreezeJudgeTest.kt     # NEW: §7 scenarios 4, 7, 9, 12 + edge variants
│   └── LoggingRulesTest.kt          # NEW: window + BONUS + created_on guards
└── data/repository/
    └── RepositoryGuardTest.kt       # NEW: require(...) ranges (moved here from deferred task-6 tests)
```

No new entities, no DAO changes, no schema change (stays version 1). If a scenario demands a column, stop and flag — schema changes are ask-first.

## Logic Rules (from data model — behavior contract, not pseudocode)

### Active-day helpers (`ActiveDays.kt`, §4.12 + FR-1)

- Bit layout: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64 (§2.1).
- `Habit.maskInForceOn(date)`: returns `prevActiveDaysMask` if `activeDaysEffectiveOn` is set **and** `date < activeDaysEffectiveOn`; otherwise `activeDaysMask`. One previous version is enough because edits always finalize first (§4.12).
- `isActiveOn(habit, date)`: checks the weekday bit of the mask in force on that date. Monday-first indexing (`DayOfWeek.value` % 7 matches Mon=bit0 … Sun=bit6).
- Helpers live in `domain/` as top-level pure functions on the existing `Habit` model — no new model types.

### `StreakCalculator` (§3; FR-8, FR-9, FR-10, FR-26, FR-27)

Inputs are in-memory (`Habit`, `List<LogEntry>`, `today`) — never DAOs.

- **Current streak (per habit):** walk back from `today`.
  - `DONE`, `BONUS`, `CLEAN` → +1, keep walking.
  - `FROZEN` → keeps alive, adds 0, keep walking.
  - `NOT_ACTIVE`, `PAUSED`, missing row (pending) → skip (no count, no break).
  - `MISSED`, `SLIPPED` → stop the walk.
- **Display rule for negative habits:** a pending *scheduled* day with no row counts as provisionally clean (+1) — silence keeps the counter running. Positive pending days stay neutral (neither count nor break).
- **Longest streak (per habit):** same walk over the full history, keep the best run.
- **Overall daily streak:** for each date, *completed* = any positive habit has `DONE`/`BONUS`; *scheduled* = any positive habit has `DONE`/`MISSED`/`FROZEN`. Walk back: completed → +1; scheduled-but-not-completed → break; anything else (nothing scheduled, or pending) → skip.
- Returns a small result type (e.g. `StreakResult(current, longest)`) — data class in the same file, no new model file.

### `Finalizer` (§4.6 + §4.5; FR-19, FR-30, FR-25)

Pure function over (habit, existing logs map, `lastFinalizedDate`, `today`) returning a **plan** (which rows to write, updated `freezeCount`/`lastMilestone`/`lastJudgedWeekStart`, new `lastFinalizedDate`) — the repository executes the plan. Logic stays JVM-testable with zero Room.

- Loop dates `D` from `max(lastFinalizedDate + 1, createdOn)` while `D <= today - 2` (finalization delay — yesterday stays editable, FR-19). In order, per habit:
  1. **Row exists → keep it.** Only allowed change: `SLIPPED` → `FROZEN` (and `freezeCount - 1`) if a freeze is available; if none, reset `lastMilestone` to 0. Never write `PAUSED`/`NOT_ACTIVE` over an existing row (the v1.0 crash). Stop.
  2. No row + paused on `D` (`pausedSince != null && D >= pausedSince`) → `PAUSED`.
  3. No row + weekday not in mask-in-force-on-`D` → `NOT_ACTIVE`.
  4. No row + scheduled: positive → `FROZEN` (-1) if `freezeCount > 0`, else `MISSED` + reset `lastMilestone` to 0; negative → `CLEAN` with `loggedAt = null` (silent).
- **Catch-up:** the loop starts after `lastFinalizedDate`, so days offline are written in order (scenario 6). Update `lastFinalizedDate` to the newest finalized date.
- **Milestone resets only:** the finalizer *resets* `lastMilestone` to 0 on real failures; *awarding* celebrations belongs to the `milestones` module (FR-25).
- **Invocation (not built here):** runs (a) at app open before UI reads state, (b) from a daily worker (worker itself = `reminders` module), (c) implicitly inside repository edit operations. Only the `Finalizer` runs this routine — never inline it elsewhere (layer contract).

### `WeeklyFreezeJudge` (§4.5, §4.7; FR-11–FR-14, FR-28, FR-30)

- Trigger: when a Sunday is finalized, for each habit whose `lastJudgedWeekStart` is before that week's Monday (Monday–Sunday weeks). Pure function returning updated `freezeCount`/`lastJudgedWeekStart` or no-op.
  1. **Skip** if partial (FR-30): `createdOn` after that Monday, or any `PAUSED` in the week.
  2. Denominator = rows except `NOT_ACTIVE`, `PAUSED`, `BONUS`. If 0 → skip the week (no error).
  3. Numerator = `DONE` rows + `CLEAN` rows **with `loggedAt != null`** (answered only — silent negatives earn nothing; `FROZEN`/`MISSED`/`SLIPPED`/silent-`CLEAN` count as not completed).
  4. If `numerator * 100 >= freezeThresholdPct * denominator` **and** `freezeCount < 3` → +1 (integer math, no float rounding; cap of 3 per habit, pools never shared).
  5. Always set `lastJudgedWeekStart` to that Monday (prevents double awards, §4.5).
- **Deferred decision (§6.2):** threshold edits during the judging lag (week ends Sunday, judged Tuesday) apply to the week being judged — accepted as-is for v1. Recorded here so `habit-edit` does not re-litigate it.

### `LoggingRules` (§4.13; FR-5, FR-6, FR-7, FR-19, FR-31)

- Single entry: `validate(habit, date, intent, today)` → resolved `DayStatus` or rejection. Repository calls it; UI never constructs rows (layer contract).
- Window: `date` in `{today, yesterday}` only, and never before `createdOn`. Anything else → reject (fail fast with `require`/`IllegalArgumentException`).
- `BONUS`: positive habit logged done on a non-scheduled day → `BONUS` (counts in streaks, excluded from weekly rate). Negative habits have no bonus days (a slip is `SLIPPED`, period).
- Days are local `YYYY-MM-DD` with a midnight cutoff (FR-7, §4.10) — no time-zone math anywhere.

### Repository completions (resolves `data-core` TODOs)

- `HabitRepository.resume`: backfill `PAUSED` for each day in `[pausedSince, yesterday]` with no row (skip finalized days — they already have rows; never overwrite `DONE`, §4.8), then clear the flag. Uses `Finalizer` date helpers, not a copy of the routine.
- `HabitRepository.editActiveDays`: finalize first, then if `activeDaysEffectiveOn != today` copy current mask to `prevActiveDaysMask`; store new mask + `activeDaysEffectiveOn = today` (§4.12).
- `HabitLogRepository.upsert`: validate via `LoggingRules` before writing (keeps the single write path + data-core's upsert-replace decision: re-taps never crash).
- `require(...)` range guards from task 6 stay; `RepositoryGuardTest` pins them as JVM tests.

## Code Style

Same as `data-core`: pure functions, `today: LocalDate` parameter, immutable data in / data out, KDoc citing the data-model section per function. Example shape:

```kotlin
// domain/WeeklyFreezeJudge.kt — pure Kotlin, no Android, today is a parameter
class WeeklyFreezeJudge {
    fun judgeWeek(habit: Habit, weekLogs: List<LogEntry>, weekMonday: LocalDate): WeekJudgment =
        TODO("implement: §4.7 steps 1-5, integer math")
}
```

`grep -r "android\." app/src/main/java/com/example/tracko/domain/` must return nothing — enforced as a success criterion, not just a wish.

## Testing Strategy (NFR-6 — the core of this slice)

JVM unit tests under `app/src/test/...` only; no instrumented tests this slice (SQLite already proven in `data-core` task 8). Each test constructs `Habit` + `List<LogEntry>` in code, passes an explicit `today`, asserts derived values. The 12 scenarios from data model §7, mapped:

| # | Scenario (§7) | Test file |
|---|---|---|
| 1 | 8×done then miss, 0 freezes → `MISSED`, current 0, longest 8 | `StreakCalculatorTest` |
| 2 | Miss with 1 freeze → `FROZEN`, count 0, streak alive +0 | `StreakCalculatorTest` + `FinalizerTest` |
| 3 | Forget Tuesday, fix Wednesday → `DONE`, no refund | `FinalizerTest` |
| 4 | Pause Wed → resume Tue → `PAUSED` rows, weeks skipped | `FinalizerTest` + `WeeklyFreezeJudgeTest` |
| 5 | Edit Mon/Wed/Fri → daily on Wed → Tuesday stays `NOT_ACTIVE` | `FinalizerTest` |
| 6 | Phone off 4 days → ordered catch-up | `FinalizerTest` |
| 7 | 4/5 days @80% earns freeze; 3 stays 3 | `WeeklyFreezeJudgeTest` |
| 8 | Mon/Wed/Fri perfect → overall survives off-days; missed Fri breaks | `StreakCalculatorTest` |
| 9 | Silent negative → full count, zero freezes | `StreakCalculatorTest` + `WeeklyFreezeJudgeTest` |
| 10 | Off-schedule log → `BONUS`, finalizer never crashes | `FinalizerTest` + `LoggingRulesTest` |
| 11 | Pause over a `DONE` → `DONE` kept | `FinalizerTest` |
| 12 | Judged week, denominator 0 → skipped, no error | `WeeklyFreezeJudgeTest` |

Plus edge variants (each is a named test): `FROZEN` adds 0 across two freezes in a row; Sunday-judged-on-Tuesday lag; threshold boundary exactly `numerator*100 == threshold*denominator`; `createdOn` mid-week partial; leap-day week; empty log list; `today` on Monday (week boundary); negative pending scheduled day displayed +1 but earns no freeze. Any change to streak/freeze/pause/inactive-day logic **requires** a test change in the same slice (architecture boundary).

## Boundaries

- **Always:** cite `FR-`/`NFR-` per change; `today` as parameter; integer percent math; existing-row-first ordering; update `docs/` if a decision changes requirements or storage.
- **Ask first:** any schema/column change, any new dependency, any behavior touching data model §6 gaps beyond the two recorded decisions (§6.2 accepted-as-is above; overall-milestone scope stays per-habit + overall until `milestones` spec).
- **Never:** store derived values (no `current_streak` column); read the clock in domain; write `PAUSED`/`NOT_ACTIVE` over existing rows; spend a freeze before finalization; let ViewModels touch DAOs; add Hilt/Koin; remove a failing test; duplicate `LoggingRules` logic in the repository.

## Success Criteria

- [ ] `domain/` holds `ActiveDays`, `StreakCalculator`, `Finalizer`, `WeeklyFreezeJudge`, `LoggingRules`; `grep -r "android\." .../domain/` empty.
- [ ] All 12 §7 scenarios pass as JVM tests, plus the listed edge variants.
- [ ] `data-core` TODOs resolved: `resume` backfills `PAUSED` without overwriting; `editActiveDays` does finalize-then-edit.
- [ ] `./gradlew test` and `./gradlew lint` green; `assembleDebug` green.

## Tasks (ordered, ≤5 files each)

1. **Active days + LoggingRules** — ✅ **DONE 2026-10-11**: `domain/ActiveDays.kt`, `domain/LoggingRules.kt`, `LoggingRulesTest` (10 tests green). *Verify: `test` + `lint` + `assembleDebug` green.*
2. **Streaks** — ✅ **DONE 2026-10-11**: `domain/StreakCalculator.kt` (`StreakResult`, two independent loops per Option B) + `StreakCalculatorTest` (10 tests green: §7.1, 7.2, 7.8a/b, 7.9 + edges). *Verify: `test` + `lint` + `assembleDebug` green.*
3. **Finalizer** — ✅ **DONE 2026-10-11 (TDD Red→Green)**: `domain/Finalizer.kt` (pure plan-returning routine, §4.6 order) + `FinalizerTest` (13 tests green: scenarios 2–6, 10, 11 + edges). *Verify: `test` + `lint` + `assembleDebug` green.*
4. **Weekly judge** — ✅ **DONE 2026-10-11 (TDD Red→Green)**: `domain/WeeklyFreezeJudge.kt` (self-guarding, integer math, cap 3) + `WeeklyFreezeJudgeTest` (12 tests green: §7.4, 7.7, 7.9, 7.12 + edges). *Verify: `test` + `lint` + `assembleDebug` green.*
5. **Repo completion** — ✅ **DONE 2026-10-11 (TDD Red→Green + mutation-checked)**: `HabitRepository` (3-DAO ctor, resume backfill, finalize-first edit, `finalizeHabit` executing plans + judging Sundays), `HabitLogRepository.upsert` (validates via rules), `LogIntent.forUserStatus`, `RepositoryGuardTest` (10 tests green incl. end-to-end freeze award), `MainActivity` wiring updated. *Verify: `test` + `lint` + `assembleDebug` green.*

## Open Questions (recorded, not blocking)

- ~~Threshold edits during judging lag~~ — **decided here (v1):** applies to the week under judgment (§6.2 accepted as-is).
- Pending-day marker on positive habits (§6.1) — deferred to `home` spec (UI concern, engine already returns neutral pending).
- Deleting a habit shrinking the overall streak (§6.3) — accepted as-is for v1 (derived, FR-4 wins); revisit only if `home` testing complains.
- Overall-streak milestones (§6.4) + time-zone travel (§6.5) — deferred to `milestones` / v2 respectively.
