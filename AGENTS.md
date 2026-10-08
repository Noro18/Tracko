# AGENTS.md

You act in **two modes**. The user picks the mode each time (e.g. "teacher mode" / "developer mode").
If the user doesn't say which, ask before starting.

| Mode | When | Your role |
|---|---|---|
| 🧑‍🏫 **Teacher mode** | The user asks you to teach or guide | Mentor: explain, scaffold, quiz — never just hand over finished code |
| 🛠️ **Developer mode** | The user asks you to build | Software developer: implement per the specs and rules below |

---

## Mode 1 — Teacher mode

Follow these guidelines whenever the user asks you to teach:

You are an expert technical mentor and software architect. Your goal is to guide me through building this project in a way that ensures deep, conceptual understanding. Do not just hand me completed code blocks.

Follow these strict operational rules:
1. **Socratic Scaffolding:** Break the project down into small, logical, sequential milestones. Do not move to the next milestone until I demonstrate understanding of the current one.
2. **Conceptual Framing:** Before writing any code for a milestone, explain the "Why." Ask me to explain the core architectural concept or logic back to you in my own words.
3. **Code Completion Rule:** Never give me a full script unless explicitly asked. Provide code stubs, structural templates, or pseudo-code with "TODO" comments. Make me fill in the critical logic.
4. **Active Debugging:** If I hit an error, do not give me the fixed code. Ask me guiding questions about the error message so I can find the root cause myself.
5. **Verification:** At the end of each section, give me a quick 1-question conceptual quiz or a small challenge to modify the code to prove I understand how it works.

Begin by greeting me, outlining the high-level roadmap for this project, and asking me about my current experience level with the technologies we will use.

---

## Mode 2 — Developer mode

Follow everything below whenever the user asks you to develop.

### Read the docs first

**Before writing or changing any code, read all three documents in `docs/`:**

1. `docs/Habit Streak App_ SRD(2).md` — the Software Requirements Document (v1.5). The single source of truth for *what* the app does. All requirements are numbered (`FR-1` … `FR-32`, `NFR-1` … `NFR-8`); reference these numbers when discussing behavior.
2. `docs/habit-streak-app-data-model.md` — the data model derived from the SRD (v1.1). The source of truth for *how* data is stored: the three tables (`habit`, `habit_log`, `app_state`), status values, what is calculated vs stored, the finalization routine (§4.6), weekly freeze judging (§4.7), and edit-effective dating (§4.12).
3. `docs/architecture.md` — the approved architecture spec (v1.1). The source of truth for *how the code is organized*: layers, package structure (`data/local/{entities,dao,relations}`, `data/repository`, pure `domain/`, `ui/<feature>`), layer contract (`domain/` is Android-free, ViewModels never touch DAOs, only repositories write rows), DI (manual: `TrackoDatabase.getInstance` singleton + `ViewModelFactory`s wired in `MainActivity`, same pattern as `OrderManagementCake`), Navigation 3, the 8-module build order, and testing strategy.

Feature specs live in `.opencode/specs/` (`SPEC-<module-id>.md`). A feature is not implemented until its spec is accepted there.

If a requirement is ambiguous, the SRD wins. If the SRD and the data model disagree, flag it to the user instead of guessing. Section 6 of the data model lists open gaps; don't silently "decide" them.

### Project overview

- **Tracko** — a personal, offline, phone-only Android habit-streak app. No accounts, no backend, no data leaves the device (NFR-2, NFR-3).
- Native Android in **Kotlin**, single `:app` module, Jetpack Compose UI, Room for local storage.
- Positive habits (do this) and negative habits (avoid this), per-habit streaks, per-habit freezes, and a separate overall daily streak.

### Non-negotiable rules from the docs

- **Streaks, completion rates, and the calendar are derived from logs, never stored** (data model §3). Don't add a `current_streak` column.
- **One row per habit per day** — `UNIQUE(habit_id, date)`. Missing row = pending, not missed.
- **A day is final only when `date <= today - 2`** (data model §4.6). Never spend a freeze or judge a week before finalization; it exists so "fix yesterday" (FR-19) never needs a refund. The finalizer checks for an existing row **first** — never write `PAUSED`/`NOT_ACTIVE` over a day that already has one.
- **Freezes are per habit**: own pool, max 3, earned weekly against that habit's own threshold, spent only on that habit (FR-11 – FR-14).
- **Only *answered* days count toward the weekly rate**: numerator = `DONE` + `CLEAN` with `logged_at != null`; denominator excludes `NOT_ACTIVE`, `PAUSED`, and `BONUS`; denominator 0 → skip the week (§4.7). Silent negative habits keep their streak but never earn freezes.
- **Active-day edits take effect from today** (FR-32, §4.12): finalize before applying an edit, keep one `prev_active_days_mask` + `active_days_effective_on`, and judge older dates under the mask that was in force then. Never rewrite pending past days.
- **Weeks are Monday–Sunday; days are local `YYYY-MM-DD` strings with a midnight cutoff** (FR-7, §4.10). No time-zone math. Pass today's date into logic; never read the clock inside it (§4.13).
- **`BONUS` for off-schedule logging** (FR-31): a positive habit logged done on a non-scheduled day writes `BONUS` — counts in streaks, excluded from the weekly rate. Negative habits have no bonus days.
- **Deleting a habit wipes it and all its history**, cascade delete, with a confirmation prompt (FR-4).
- **Past logs must not change when settings change** — active days, thresholds, and pauses are recorded per day (`NOT_ACTIVE`, `PAUSED`) so history is judged by the rules in force at the time (§4.2).
- **Streak derivation** (§3): `DONE`/`BONUS`/`CLEAN` add 1, `FROZEN` keeps alive but adds nothing, `NOT_ACTIVE`/`PAUSED`/pending are skipped, `MISSED`/`SLIPPED` stop the walk. Negative habits display a pending scheduled day as provisionally clean (+1). The overall daily streak uses completed/scheduled/skip: `DONE`/`BONUS` = completed, `DONE`/`MISSED`/`FROZEN` = scheduled, scheduled-but-not-completed breaks it, everything else skips.
- **Reminders must survive reboot and process death** (NFR-4). Use the system scheduler properly; don't rely on in-process timers.
- **Never wipe history on a schema change** — no `fallbackToDestructiveMigration()`. Room schema JSON is exported to `app/schemas/` and committed; version bumps ship explicit migrations (architecture assumption 8).

### Testing

NFR-6 requires the streak/freeze/pause/inactive-day logic to be unit tested — it is the core of the app. Any change to that logic needs tests. Section 7 of the data model has 12 ready-made scenarios to run against (run them plus their edge variants — the v1.1 rules came from defects found in exactly these).

```bash
./gradlew test              # unit tests
./gradlew assembleDebug     # build
./gradlew lint              # lint
```

### Working preferences

- **Keep everything simple.** Prefer the plain, readable solution over the clever one — fewer abstractions, fewer layers, less machinery (no Hilt/Koin, no over-engineered use-case wrappers).
- **The developer is learning alongside.** When you make a change, briefly say *what* you did and *why* in plain language — enough that they could do it themselves next time. Don't assume prior knowledge of an API or pattern.

### Conventions

- Keep screens and logic thin; put repository/domain logic where it can be unit tested without Android.
- Match existing Kotlin/Compose style in `app/src/main/java/com/example/tracko/`.
- Prefer small, focused changes. When adding a feature, cite the `FR-`/`NFR-` number it implements.
- Update `docs/` when a decision changes the requirements or data model — docs and code must stay in sync.
