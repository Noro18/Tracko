# Tracko Architecture (v1)

**Status:** APPROVED (2026-10-07)
**Author:** opencode
**Governing docs:** `docs/Habit Streak App_ SRD(2).md` (SRD v1.5), `docs/habit-streak-app-data-model.md` (data model v1.1)

---

## Objective

Establish the architecture for Tracko — a personal, offline, phone-only Android habit-streak app — so that every subsequent feature is built the same way: MVVM + unidirectional data flow, domain logic testable on the JVM, and one capability at a time, spec-gated.

This spec covers **structure and conventions only**. Feature behavior is defined by the SRD; storage behavior by the data model. When those disagree with this spec, they win — flag the conflict instead of coding around it.

**Success looks like:** a running app where `data-core` + `streak-engine` pass all 12 scenarios from data model §7 as JVM unit tests, each screen reads state from a `StateFlow<UiState>` held by its own ViewModel, and no domain function reads the clock.

### Assumptions (approved via Q&A)

1. Single `:app` module, package-by-feature. No multi-module split for v1.
2. Room is the local database (NFR-2).
3. Unidirectional MVVM: Compose ← `StateFlow<UiState>` ← ViewModel ← UseCase ← Repository ← Room.
4. Domain logic has **zero Android imports** and takes `today` as a parameter (data model §4.13).
5. **Manual DI** via `AppContainer` on the `Application` class (no Hilt/Koin).
6. **Navigation 3** (`navigation3-runtime` + `navigation3-ui`), not androidx.navigation.
7. Specs live in `.opencode/specs/`; features are not implemented until their spec is accepted.

---

## Tech Stack

| Concern | Choice | Notes |
|---|---|---|
| Language / UI | Kotlin 2.0.21, Jetpack Compose (BOM 2024.09), Material 3 | existing |
| DB | Room + KSP | schema = data model §2 exactly |
| Date API | `java.time.LocalDate` | needs **core library desugaring** (minSdk 24 < 26) |
| DI | Manual `AppContainer` | set in `TrackoApplication`, read via `application` in composables |
| MVVM glue | `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | `collectAsStateWithLifecycle()` |
| Navigation | Navigation 3 (`navigation3-runtime`, `navigation3-ui`) | typed `NavKey` entries |
| Async | Kotlin coroutines + Flow | repos expose `Flow`, use cases are `suspend`/pure |
| BG work | WorkManager | daily finalizer + re-reminders (NFR-4) |
| Unit tests | JUnit4 + `kotlinx-coroutines-test` | `domain/` runs on plain JVM |
| DAO tests | `room-testing` (in-memory, instrumented) | constraint verification |

New entries go through `gradle/libs.versions.toml`; exact versions pinned at implementation time (first slice), chosen compatible with AGP 8.13.2 / Kotlin 2.0.21 — verify with a build before proceeding.

---

## Project Structure

```
app/src/main/java/com/example/tracko/
├── TrackoApplication.kt          # AppContainer owner; WorkManager configuration
├── MainActivity.kt               # setContent { TrackoNavHost() } only — no logic
├── navigation/                   # Nav3 NavHost, NavKey definitions, deep-link routes
├── core/
│   ├── data/
│   │   ├── db/                   # TrackoDatabase, HabitEntity, HabitLogEntity,
│   │   │                         # AppStateEntity, DAOs, Converters, Migrations
│   │   └── repository/           # HabitRepository, LogRepository, AppStateRepository
│   ├── domain/
│   │   ├── model/                # Habit, LogEntry, DayStatus, WeekJudgment (plain Kotlin)
│   │   ├── StreakCalculator.kt   # data model §3 (per-habit + overall daily)
│   │   ├── Finalizer.kt          # data model §4.6, existing-row-first
│   │   ├── WeeklyFreezeJudge.kt  # data model §4.7, integer math
│   │   └── LoggingRules.kt       # data model §4.13 (today/yesterday, BONUS, created_on)
│   └── notifications/            # ReminderScheduler, receivers, boot receiver (NFR-4)
├── feature/
│   ├── home/                     # HomeScreen + HomeViewModel + HomeUiState
│   ├── habitdetail/              # calendar grid, stats, freezes (FR-20–22)
│   ├── edithabit/                # create/edit/pause/delete (FR-1–4, FR-32)
│   ├── widget/                   # Glance-style display-only widget (FR-23/24)
│   └── milestones/               # 50/100/150 celebrations (FR-25)
└── ui/theme/                     # existing (Color, Theme, Type)

app/src/test/java/com/example/tracko/      # mirrors main: domain tests, ViewModel tests
app/src/androidTest/...                    # DAO/constraint tests only
```

### Layer contract (enforced in review)

- **`core/domain` must not import anything under `android.*` or `androidx.*`.** All functions that need a date take it as a parameter. This is what makes NFR-6 tests fast and deterministic.
- **ViewModels call use cases / repositories only — never DAOs.** Each screen has exactly one `UiState` data class, immutable, exposed as `StateFlow`.
- **Composables observe state and send events — no business logic, no repository calls.**
- **Only repositories write rows.** The repository owns the `finalize-then-edit` transaction (§4.12): finalize first, then mutate `active_days_mask`/`prev_active_days_mask`/`active_days_effective_on` atomically.
- **Only the `Finalizer` runs the finalization routine**, invoked (a) at app open before UI reads state, (b) from a daily `PeriodicWorkRequest`, (c) implicitly inside repository edit operations. Never inline the routine anywhere else.
- **Single write path for check-ins:** screen/notification event → ViewModel/use case → `LoggingRules.validate()` → repository. UI never constructs a `HabitLogEntity` directly.

### Canonical code style (one snippet beats three paragraphs)

```kotlin
// core/domain/StreakCalculator.kt — pure Kotlin, no Android, today is a parameter
class StreakCalculator {
    fun currentStreak(logs: List<LogEntry>, activeMask: Int, today: LocalDate): StreakResult =
        walkBack(logs, activeMask, today, /* displayPendingAsClean = */ false)
}

// feature/home/HomeViewModel.kt
class HomeViewModel(
    private val observeToday: ObserveTodayUseCase,
    private val logToday: LogTodayUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onDoneTap(habitId: Long) = viewModelScope.launch { logToday(habitId) }
}

data class HomeUiState(
    val rows: List<HabitRowUi> = emptyList(),
    val overallStreak: Int = 0,
)
```

Naming: `XxxEntity` (Room), `Xxx` in `domain/model` (pure), `XxxRepository`, `XxxUseCase`/`ObserveXxx` (only when a use case does more than delegate), `XxxViewModel`, `XxxUiState`, `XxxScreen` (composable).

---

## Capability Map (build order — gated)

| Module id | Responsibility | Depends on |
|---|---|---|
| `data-core` | Room schema (3 tables, constraints, cascade), DAOs, repositories, `AppContainer` | — |
| `streak-engine` | `StreakCalculator`, `Finalizer`, `WeeklyFreezeJudge`, `LoggingRules` | `data-core` |
| `home` | Today screen, one-tap check-in, overall daily streak | `streak-engine` |
| `habit-detail` | Month calendar, current/longest stats, freezes available | `streak-engine` |
| `habit-edit` | Create/edit/pause/delete + §4.12 edit-effective dating | `data-core`, `streak-engine` |
| `reminders` | Per-habit notifications with actions, re-remind, reboot survival | `home` |
| `widget` | Display-only home-screen widget | `home` |
| `milestones` | 50/100/150 celebrations | `streak-engine` |

**Order:** data-core → streak-engine → (home, habit-edit, habit-detail in any order) → reminders → widget → milestone.
**Gates:** each module gets accepted before the next begins; a slice must pass `./gradlew test` + `./gradlew lint` before its successor starts. Slices stay small — no task touches more than ~5 files.

---

## Testing Strategy (NFR-6)

| Level | Where | Covers |
|---|---|---|
| JVM unit | `app/src/test/.../domain/` | All 12 §7 scenarios + their edge variants; streak walk (incl. pending negative display), finalizer ordering incl. existing-row-first, weekly integer math + denominator-0 skip, §4.12 mask selection, BONUS handling |
| JVM unit | `app/src/test/.../feature/` | ViewModel tests: `StandardTestDispatcher`, assert `UiState` transitions per event |
| Instrumented | `app/src/androidTest/.../db/` | `UNIQUE(habit_id,date)`, CHECK constraints, cascade delete, converters |
| Manual | device | Notifications fire/re-fire, reboot survival, widget, milestone dialogs |

Any change to streak/freeze/pause/inactive-day logic **requires** a test change in the same slice. The 12 scenarios are regression-locked.

Commands: `./gradlew test`, `./gradlew assembleDebug`, `./gradlew lint`.

---

## Boundaries

- **Always:** cite the `FR-`/`NFR-` number a change implements; keep `domain/` Android-free; pass `today` into logic; update `docs/` + this spec when a decision changes requirements or storage; run test + lint before ending a slice.
- **Ask first:** Room schema changes (new column/table/status), adding any dependency, changing version catalog entries beyond the stack table, touching `proguard` or Gradle config, anything in SRD §6 gaps.
- **Never:** store derived values (streaks, rates), read the clock inside domain code, write `PAUSED`/`NOT_ACTIVE` over an existing row, spend a freeze before finalization, let a UI layer touch a DAO, add Hilt/Koin, remove a failing test.

---

## Success Criteria

- [x] Spec accepted by human.
- [ ] `data-core`: Room schema matches data model §2 column-for-column; constraint + cascade tests pass.
- [ ] `streak-engine`: all 12 §7 scenarios pass as JVM tests; `grep -r "android\." app/src/main/java/com/example/tracko/core/domain/` returns nothing.
- [ ] First screen (`home`): state flows through `StateFlow<UiState>` only; tapping Done creates one row via repository.
- [ ] `./gradlew test` and `./gradlew lint` green at every slice gate.

## Open Questions (deferred — data model §6, decide in feature specs)

1. Pending-day UI on positive habits (unconfirmed marker?).
2. Threshold edits during the judging lag (next-Monday vs per-week snapshot).
3. Deleting a habit shrinks overall daily streak — accept or add `daily_summary`?
4. Overall-streak milestones as well as per-habit?
5. Time-zone travel behavior — accept as-is for v1?
