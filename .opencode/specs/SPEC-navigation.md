# Spec: `navigation` (shell before Module 3)

**Status:** DRAFT — awaiting human approval.
**Author:** opencode | **Date:** 2026-10-11
**Parent:** `docs/architecture.md` v1.1 (approved)
**Deviation (recorded, author-chosen 2026-10-11):** architecture assumption 6 prescribes
Navigation 3, but the author knows classic Navigation 2 from past apps and prefers it —
`NavController` owns the backstack, no manual stack management, less boilerplate.
Navigation 3’s typed keys are deliberately traded for routes the author already reads fluently.
**Implements:** app skeleton only — destinations, bottom bar, per-screen packages. No behavior yet.
**Depends on:** `streak-engine` (ACCEPTED, on `main`). Blocks: `home`, `habit-edit`, `habit-detail`, `milestones`.

---

## Objective

One navigation file owns every screen change; each screen lives in its own package with its own ViewModel; the navigation file only decides *what opens when a button is tapped*. Built with classic Navigation 2 (`NavController` + `NavHost` + string routes — the pattern the author already knows).

**Done means:** the app launches to an empty Home shell, the bottom bar (Home, `+`, Milestone per the mockup) switches destinations, back works, and `./gradlew test` + `./gradlew lint` + `assembleDebug` are green.

## Tech Stack (changes)

| Addition | Version |
|---|---|
| `androidx.navigation:navigation-compose` | `2.9.8` (latest stable compiling against SDK 36 — 2.10.2 needs SDK 37, beyond AGP 8.13.2's max; verified by real build 2026-10-11) |

One artifact in `gradle/libs.versions.toml` as `navigationCompose`; single `implementation` line in `app/build.gradle.kts`. Compose-only — no fragment/ui/testing artifacts. No serialization plugin (classic string routes, not type-safe destinations — deferred until a route typo actually bites us).

## Project Structure (files created / modified)

```
app/src/main/java/com/example/tracko/
├── MainActivity.kt                      # MODIFY: builds DB → repos → factories → TrackoNavGraph
├── ui/
│   ├── navigation/
│   │   └── TrackoNavGraph.kt            # NEW: THE navigation file — NavHost, Routes, bottom bar;
│   │                                    #   the ONLY file that calls navigate()/popBackStack()
│   ├── home/
│   │   ├── HomeScreen.kt                # NEW shell: title only + onAddClick/onHabitClick stubs
│   │   ├── HomeViewModel.kt             # NEW shell: empty HomeUiState StateFlow
│   │   ├── HomeUiState.kt               # NEW: data class (rows + overallStreak, empty defaults)
│   │   └── HomeViewModelFactory.kt      # NEW: takes repositories (wired in MainActivity)
│   ├── habitdetail/                     # NEW shell: DetailScreen(habitId) + ViewModel + UiState + Factory
│   ├── edithabit/                       # NEW shell: EditScreen(habitId: Long?) + ViewModel + UiState + Factory
│   └── milestones/                      # NEW shell: MilestonesScreen + ViewModel + UiState + Factory
```

Screens are placeholders (a title + the buttons the mockup needs). Real UI arrives in the feature modules; this slice only proves every tap lands somewhere.

## Navigation Rules (the contract)

- **Classic string routes, one table** (owned by `TrackoNavGraph.kt`):
  ```kotlin
  object Routes {
      const val HOME = "home"
      const val DETAIL = "detail/{habitId}"          // Long, required
      const val EDIT = "edit?habitId={habitId}"       // Long?, null = center + button (create new)
      const val MILESTONES = "milestones"

      fun detail(habitId: Long) = "detail/$habitId"
      fun edit(habitId: Long?) = if (habitId == null) "edit" else "edit?habitId=$habitId"
  }
  ```
  Builders (not raw strings) at call sites — one typo-fix location if a route ever changes.
- **Tabs are peers, sub-screens are layers (decided 2026-10-11).** Home/Milestones switch with save/restore state; Edit/Detail push plainly on top of a tab and leave via `popBackStack()` (`onDone`/`onBack` callbacks). The bottom bar shows **only on peer screens** — mixing sub-screens into the always-visible bar caused the stuck-Edit mess (duplicate Edits poisoned tab restore). The `+` then can only be pressed from Home/Milestones, so stacking can't happen. `launchSingleTop` stays on `+` as a double-tap guard.
- **Screens never touch the controller.** Each screen exposes tap lambdas (`onHabitClick(id)`, `onAddClick()`, `onMilestonesClick()`…); only the graph turns them into `navigate()` calls. `Detail` reads its required `Long` arg; `Edit` uses `Routes.NO_ID = -1L` as the create-new sentinel — `NavType.LongType` is a primitive `long` and **rejects null** (crashed on launch 2026-10-11, fixed same day), so "no id" is `-1L`, never null.
- **Bottom bar** (mockup): Home tab (habit list), center `+` action (opens `edit` fresh), Milestones tab. **Decided 2026-10-11:** Milestones *is* the statistics screen — whole-app streaks + overall milestones (from `app_state.overall_last_milestone`), never per-habit (those live in `habit-detail`). Tab taps use the standard classic pattern — `launchSingleTop = true` + `popUpTo(start)` with `saveState`/`restoreState` — so re-tapping a tab returns to its saved state instead of stacking duplicates. Selected state from `currentBackStackEntryAsState()`. Bell/settings icons from the mockup are **absent** in v1 — reminders/settings are later modules.
- **ViewModels per screen, built by factories:** `viewModel(factory = ...)` inside each `composable()`; ViewModels never navigate and never touch DAOs (layer contract).
- **DI stays manual:** `MainActivity` builds `TrackoNavGraph(homeFactory, detailFactory, editFactory, milestonesFactory)` — same `getInstance → repos → factories` chain as `data-core` task 7, extended. No `AppContainer`/`Application`, no Hilt/Koin.

## Code Style

Cake-style file layout (`ui/<feature>/XxxScreen.kt + XxxViewModel.kt + XxxUiState.kt + XxxViewModelFactory.kt`), one graph file in Cake's `ui/navigation/` spot:

```kotlin
// ui/navigation/TrackoNavGraph.kt — the only file that changes screens
@Composable
fun TrackoNavGraph(homeFactory: HomeViewModelFactory, /* … */) {
    val navController = rememberNavController()
    Scaffold(bottomBar = { TrackoBottomBar(navController) }) { _ ->
        NavHost(navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onHabitClick = { navController.navigate(Routes.detail(it)) },
                    onAddClick = { navController.navigate(Routes.edit(null)) },
                )
            }
            composable(Routes.DETAIL, arguments = listOf(navArgument("habitId") { type = NavType.LongType })) {
                DetailScreen(habitId = it.arguments?.getLong("habitId") ?: return@composable)
            }
            composable(
                Routes.EDIT,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType; nullable = true; defaultValue = null }),
            ) { EditScreen(habitId = it.arguments?.getLong("habitId")) }
            composable(Routes.MILESTONES) { MilestonesScreen() }
        }
    }
}
```

## Testing Strategy

- **Unit:** none this slice — shells hold no logic (same precedent as `data-core` tasks 1–5). ViewModel tests arrive with each feature module (`StandardTestDispatcher`, assert `UiState` per event).
- **Manual (on your device):** launch → Home shell; tap `+` → Edit(new); back → Home; tap a (stub) habit row → Detail; Milestone tab → Milestones shell; back through the stack to exit. Deep links explicitly out of scope for v1.
- **Verify:** `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`.

## Boundaries

- **Always:** `navigation-compose` version pinned here and verified by real build output; routes built via `Routes` builders; navigation calls only in `TrackoNavGraph.kt`; screens thin (title + buttons).
- **Ask first:** any tab beyond Home/`+`/Milestones; deep links; nested graphs; any dependency beyond the one artifact.
- **Never:** Navigation 3 artifacts; manual backstack lists; ViewModels navigating or holding a `NavController`; screens importing navigation; Hilt/Koin; logic inside shell screens/ViewModels (that belongs to the feature specs).

## Success Criteria

- [ ] `navigation-compose` resolves and `assembleDebug` passes with the version above.
- [ ] `TrackoNavGraph.kt` is the only file calling `navigate()`/`popBackStack()` (`grep -r "navigate(" ui/ --include="*.kt" -l` shows only it).
- [ ] 4 shells render behind the bottom bar; every mockup tap lands on the right shell.
- [ ] `./gradlew test` and `./gradlew lint` green.

## Tasks (ordered, ≤5 files each)

1. **Deps** — ✅ **DONE 2026-10-11**: catalog `navigationCompose = "2.9.8"` + one `implementation` line (2.10.2 rejected: needs compileSdk 37). *Verified: `assembleDebug` green.*
2. **Routes + graph + bottom bar** — ✅ **DONE 2026-10-11**: `ui/navigation/TrackoNavGraph.kt` (Routes builders, NavHost, 3-button bar with `+` action, inline tap-through placeholders for Task 3 to replace; Star icon — trophy lives in extended set, deferred). *Verified: `assembleDebug` + `test` + `lint` green.*
3. **Shells** — ✅ **DONE 2026-10-11**: 4 packages × (Screen + ViewModel + UiState + Factory); graph rewired to real screens with per-destination `viewModel(factory)`; placeholders deleted. *Verified: `assembleDebug` + `test` + `lint` green.*
4. **Wiring** — ✅ **DONE 2026-10-11**: `MainActivity` builds 4 factories → `TrackoNavGraph(...)`; Greeting deleted. *Verified: `assembleDebug` + `test` + `lint` green (app launches to Home shell).*

## Open Questions (deferred)

- Type-safe destinations (serialization plugin) — only if a route typo actually bites us.
- Transitions/animations, nested graphs, deep links — only if a feature spec demands them.
- Bell/settings icons from the mockup — arrive with `reminders`/settings, not here.
