# Spec: `home` (Module 3, screen 1)

**Status:** ACCEPTED — approved by developer 2026-10-11 (Task 1 plan + FR-20a wording). Implementation on hold per developer request.
**Author:** opencode | **Date:** 2026-10-11
**Parent:** `docs/architecture.md` v1.1 + `references/habit-tab.png` (mockup — visual source of truth for layout)
**Implements:** Today screen, one-tap check-in, overall daily streak (FR-5, FR-6, FR-8–FR-10, FR-26, FR-27)
**Depends on:** `navigation` (ACCEPTED? — verify), `streak-engine` (`StreakCalculator`, `LoggingRules`, `finalizeHabit`)

---

## Objective

The screen the user opens every day: greeting + overall streak + today's progress, week strip, type filter tabs, and one card per habit with a single action button. Tapping Done/No-slip writes exactly one row through the repository (FR-31 `BONUS` handled by `LoggingRules`), and the button greys the moment today is logged (the rule the author set in Task 8).

**Done means:** the screen matches the mockup's structure (header, week strip, tabs, cards, action states), all taps write via `HabitLogRepository.upsert(entry, habit, today)`, finalization runs at open before first read, and `./gradlew test` + `./gradlew lint` + `assembleDebug` are green plus a manual tap-through on device.

## Layout (decided 2026-10-11: keep v1 minimal — header, overall streak, cards)

1. **Top bar** — "Tracko" title only. No bell/gear in v1 (later modules).
2. **Header card** — overall daily streak big ("N-day streak 🔥", N = 0 → "Start your first streak today!"). No Daily Resolution bar, no week strip, no filter tabs in v1 — cut for simplicity, revisit only on request.
3. **Habit cards** (one per habit, all types together, creation order), each a row:
   - left block: name (ellipsis) with a compact **stat-labels row** underneath — `🔥 streak · longest N · ❄ freezes · n/7 rate` as small labels (labels, not a side column — decided 2026-10-11: a side stats column eats too much width);
   - middle: **GitHub-style heatmap** — 12 week-columns × 7 squares on the card's left (builder default stays 16; the card requests 12), ~10dp squares, Monday first (FR-20 style). Right side holds a reserved 48dp slot for the check button (lands in the button task);
   - cell colors: `DONE`/`BONUS`/`CLEAN` green, `FROZEN` ice-blue (FR-14: frozen looks different), `MISSED`/`SLIPPED` red, `NOT_ACTIVE`/`PAUSED` dim, no row = dark empty (pending, §2.2 — no invented marker per §6.1). Cell data derived, never stored;
   - right: **round check button** — positive: writes `DONE` (or `BONUS` off-schedule); negative: writes `CLEAN`. Enabled only when a user tap is legal today (scheduled + no user row yet); otherwise greyed-disabled. Unscheduled positive today → disabled with no label change.
   - tapping the card body (not the button) → `onHabitClick(id)` → Detail (graph already wires this).
4. Cards observe `HomeUiState`; the screen holds zero logic. Zero-state: "No habits yet — tap + to create one."

## ViewModel contract

```kotlin
data class HeatCell(val date: LocalDate, val status: DayStatus?) // null = pending/empty
data class HabitRowUi(
    val habit: Habit,
    val streak: Int,
    val longest: Int,
    val freezes: Int,
    val rate7: String,              // "n/7"
    val heatmap: List<List<HeatCell>>, // 12 trailing weeks × 7 weekday rows
    val checkEnabled: Boolean,      // legal user tap today?
)
data class HomeUiState(
    val overallStreak: Int = 0,
    val rows: List<HabitRowUi> = emptyList(),
)
// events: onCheckTap(habitId)
```

- Owns: `finalizeAll(today)` at init before first emit (app-open finalization — the "(a)" invocation from the architecture contract; the daily worker comes in `reminders`); combines `observeHabits` + per-habit `observeLogs` into `StateFlow<HomeUiState>`; derives streaks/rates/heatmap via `StreakCalculator` (pure, injected or constructed — no DAO).
- Check-ins go ViewModel → `HabitLogRepository.upsert(entry with `loggedAt = now`, habit, today)`; the button disables off the fresh state (single write path, upsert-replace makes re-taps safe).
- Cell colors live next to the UI as one pure `statusToHeatColor(status): Color` mapping (untestable-compose boundary kept thin; the *data* — which status per cell — is ViewModel-derived and unit-tested).
- `today: LocalDate` is a constructor parameter (default = real today) — tests inject Sundays and midnights deterministically; production passes `LocalDate.now()` once at the call site (`MainActivity` factory), never inside domain.
- New repo surface (small, same slice): `HabitRepository.finalizeAll(today)` — loops habits through existing `finalizeHabit` + persists; no new DAO methods.

## Code Style

Same shells, grown up: `HomeScreen` observes `uiState`, renders sections 1–5, sends events; `components/` gets only what two screens share (nothing yet — no premature shared widgets). Dark-first colors reuse `TrackoGreen`/`TrackoGrey`; card shape matches mockup (16dp-ish rounded dark cards).

## Testing Strategy (TDD Red→Green per AGENTS.md)

- **JVM (`HomeViewModelTest`, fake DAOs like `RepositoryGuardTest`):** empty state on no habits; rows derive streak/heatmap/rate from seeded rows; `onCheckTap` writes exactly one user row with `loggedAt != null` (positive `DONE`, negative `CLEAN`); second tap stays disabled (state, not crash); off-schedule positive tap stores `BONUS`; `finalizeAll` runs before first non-empty emit (seed stale `lastFinalizedDate`, assert bookmark advanced + `MISSED` written); overall streak from seeded multi-habit logs.
- **Manual (device):** mockup walk — header numbers, week highlight, tabs filter, Mark Done → Completed ✓ + counts move, No Slip Today → disabled, `+` → Edit, card → Detail, rotation keeps state.
- **Verify:** `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`.

## Boundaries

- **Always:** cite FR per change; `today` injected; derived-never-stored; only repos write rows.
- **Ask first:** any new DAO method beyond `finalizeAll`'s needs (none expected); touching `Habit`/`LogEntry` shapes; bell/gear doing anything.
- **Never:** streak/rate math in the composable; `LocalDate.now()` in domain or ViewModel logic paths (once at factory call site); navigation inside the ViewModel; a `current_streak` column.

## Success Criteria

- [ ] `HomeUiState` drives every pixel (no repo/DAO imports in `HomeScreen`).
- [ ] All ViewModel tests green; `test` + `lint` + `assembleDebug` green.
- [ ] Manual mockup tap-through passes on device.
- [ ] `finalizeAll` runs at open (stale-bookmark proof in tests).

## Tasks (ordered, ≤5 files each)

1. **Card logic + card UI — ✅ DONE 2026-10-11 (Red→Green).** `HeatmapBuilderTest` run red first (`Unresolved reference 'HeatmapBuilder'`), then `Heatmap.kt` + `HabitRowUi`/`rows` + `HabitCard` green (4/4 new, 60 total). `test` + `assembleDebug` + `lint` green, `domain/` Android-free. Demo card wired temporarily in `HomeScreen` (TODO: Task 2 replaces with real rows) — visible in-app, verified alive on device.
   Files (4 — limit ~5 per slice):
   ```
   app/src/test/java/com/example/tracko/domain/HeatmapBuilderTest.kt   (NEW, written first)
   app/src/main/java/com/example/tracko/domain/Heatmap.kt              (NEW)
   app/src/main/java/com/example/tracko/ui/home/HomeUiState.kt         (EDIT: add HabitRowUi + rows)
   app/src/main/java/com/example/tracko/ui/home/HabitCard.kt           (NEW)
   ```
   **Decisions (do not change):** grid logic is pure Kotlin in `domain/`, `today` as parameter, no clock, no `android.*` imports; card receives one immutable `HabitRowUi` and only draws it (never touches ViewModel/repository/DAO); 16 weeks default (builder parameter); no row = pending (§2.2); future days render transparent; pending = muted empty square, no invented marker (§6.1 — flag if it comes up).
   **Step 1 RED:** write `HeatmapBuilderTest` (grid is weeks-columns×7 rows; every column starts Monday and the last is this week; days after `today` marked future; statuses come from logs, no row = null). Run `./gradlew test` — it **must fail to compile** (`HeatmapBuilder` doesn't exist). That failure is the proof.
   **Step 2 GREEN:** smallest `Heatmap.kt` — `HeatCell(date, status, isFuture)` + `HeatmapBuilder.build(logs, today, weeks = 16)` indexing logs by date from the Monday of `(weeks-1)` weeks ago. All new tests pass, existing stay green.
   **Step 3 UI state:** `HomeUiState` keeps existing fields (`HomeScreen` still reads `habitCount`) + new `HabitRowUi(id, name, currentStreak, freezeCount, heatmap)` (FR-20, FR-21, FR-22).
   **Step 4 Card:** `HabitCard(row, onClick)` — name, 🔥 streak label, ❄️ freeze label, heatmap (`Row` of week-`Column`s, `weight(1f)` per column, `aspectRatio(1f)` squares, status colors incl. FR-14 blue-vs-green) + `@Preview` with seeded logs. No ViewModel/repository/DAO reference.
   **Verify (acceptance for Task 1):** red-first run shown; `test` green; `assembleDebug` green; `lint` clean; `grep -r "android\." .../domain/` empty; preview renders (green/blue/red/muted squares); card draws `HabitRowUi` only.
2. **Real data in the cards — ✅ DONE 2026-10-11 (Red→Green).** `HomeViewModelTest` (4 tests: empty state, seeded rows, finalize-before-emit, 2-habit overall) was proven Red (`Too many arguments for constructor`), then greened: `HabitRepository.finalizeAll(today)` (loop, no new DAO), `HomeViewModel` grew `today` param + `init { finalizeAll → combine(observeHabits, dataVersion).collect { rebuild } }`, `MainActivity` passes `LocalDate.now()` once at the factory, `HomeScreen` renders `LazyColumn { items(rows, key = { it.id }) }` + zero-state (demo card deleted). 64 tests green; `test`+`lint`+`assembleDebug` green; alive on device.
   Files (5):
   ```
   app/src/test/java/com/example/tracko/ui/home/HomeViewModelTest.kt   (RED written first)
   app/src/main/java/com/example/tracko/data/repository/HabitRepository.kt  (EDIT: +finalizeAll)
   app/src/main/java/com/example/tracko/ui/home/HomeViewModel.kt       (rewritten)
   app/src/main/java/com/example/tracko/ui/home/HomeViewModelFactory.kt (EDIT: +today)
   app/src/main/java/com/example/tracko/ui/home/HomeScreen.kt          (LazyColumn, demo deleted)
   ```
   **Task 2 decisions (do not change):** finalize runs before first emit (one coroutine: finalize, THEN collect); rebuild on habit-table changes + a `dataVersion` trigger (Task 3 bumps it after writes) — no per-habit flow combining; logs fetched once per rebuild from `min(createdOn)` to `today` (full history, streak correctness > window size); `checkEnabled = false` until Task 3 computes it; spec's `HabitRowUi(habit, streak, longest, freezes, rate7)` shape **superseded** by flatter `id, name, type, currentStreak, freezeCount, heatmap, checkEnabled` (card holds no `Habit` object).
3. **Check button + header + overall streak** (remaining — `onCheckTap` → `LoggingRules` → `repo.upsert`, real `checkEnabled`, bump `dataVersion`, header card with overall streak, longest + n/7 labels, tap wiring test Red→Green).

## Boundaries (from `docs/architecture.md`)

- **Always:** cite the `FR-`/`NFR-` numbers a change implements, keep `domain/` Android-free, pass `today` into logic, run test + lint before ending the slice.
- **Ask first:** Room schema changes, adding any dependency, touching Gradle/proguard config, anything in data model §6 gaps; a new `HabitLogRepository` read for Task 2.
- **Never:** store derived values (streaks, rates), read the clock inside domain code, let a UI layer touch a DAO, add Hilt/Koin, remove a failing test.

## Docs to keep in sync

The SRD places the calendar on the detail screen (FR-20) — a heatmap on the Home card is a small addition. **Proposed (asking, not doing):** one-line SRD addition — *"FR-20a: Each habit card on Home shows a compact heatmap of recent weeks with the same day coloring as the detail calendar."* Approve the wording and I'll edit `docs/`.

## Open Questions (deferred)

- Debug seeder shipped early (2026-10-18, developer choice): `data/local/DebugSeeder.kt` + `MainActivity` gate (`FLAG_DEBUGGABLE` + empty-DB) — 2 habits ("Run" POSITIVE 45d, "No sugar" NEGATIVE 30d) + history logged via `HabitLogDao` (bypasses §4.13 window *by design*: simulates pre-existing historical days), then real `finalizeAll` inks gaps. **Delete when the Edit module ships.**

- Bell badge count + gear destination — `reminders`/settings modules.
- Scrollable week strip, pull-to-refresh, card swipe actions — only if the author asks after v1.
