# Spec: `data-core` (Module 1 of 8)

**Status:** DRAFT — awaiting human approval.
**Author:** opencode | **Date:** 2026-10-07 (revised same day)
**Parent:** `docs/architecture.md` v1.1 (approved) → capability map module `data-core`
**Implements:** storage half of data model v1.1 (§2, §4.13); no behavior yet
**Structure follows:** the `OrderManagementCake` conventions the author approved (data/local split, MainActivity DI) with the two deliberate deviations recorded in architecture assumption 8.

---

## Objective

Persist the three tables exactly as specified in the data model, expose them through Flow-based DAOs and repositories, and wire everything the way the author already knows: DB singleton + per-feature factories in `MainActivity`. No UI, no streak/domain logic — that is slice 2 (`streak-engine`).

**Done means:** the schema matches data model §2 column-for-column with DB-enforced constraints, the schema JSON is committed to VCS, repositories are the only write path, and `./gradlew test` + `./gradlew lint` + `assembleDebug` are green.

## Tech Stack (changes) — ✅ all verified with `assembleDebug` + `test` + `lint` on 2026-10-07

| Addition | Version |
|---|---|
| KSP plugin (`com.google.devtools.ksp`) | `2.0.21-1.0.28` — ✅ verified (proven in `OrderManagementCake`) |
| `androidx.room:room-runtime`, `room-ktx`, `room-testing` | `2.7.1` — ✅ verified |
| `androidx.room:room-compiler` (KSP) | `2.7.1` — ✅ verified |
| `com.android.tools:desugar_jdk_libs` (core library desugaring) | `2.1.4` — ✅ verified |
| `kotlinx-coroutines-test` | `1.9.0` — ✅ verified |
| `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | `2.10.0` — ✅ verified (shares `lifecycleRuntimeKtx` ref) |

All live in `gradle/libs.versions.toml`; `app/build.gradle.kts` applies `alias(libs.plugins.ksp)`, declares `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, and has `isCoreLibraryDesugaringEnabled = true` with `coreLibraryDesugaring(libs.desugar.jdk.libs)`.

## Project Structure (files created)

```
app/src/main/java/com/example/tracko/
├── MainActivity.kt                      # MODIFY: DB singleton → repos → factories (wiring only)
├── data/local/
│   ├── TrackoDatabase.kt                # @Database(version=1, exportSchema=true),
│   │                                    #   @TypeConverters(Converter), getInstance(context)
│   ├── Converter.kt                     # LocalDate ↔ "YYYY-MM-DD", DayStatus ↔ String
│   ├── entities/
│   │   ├── HabitEntity.kt               # table `habit`
│   │   ├── HabitLogEntity.kt            # table `habit_log`
│   │   └── AppStateEntity.kt            # table `app_state` (single row, id = 1)
│   ├── dao/
│   │   ├── HabitDao.kt
│   │   ├── HabitLogDao.kt
│   │   └── AppStateDao.kt
│   ├── relations/
│   │   └── HabitWithLogs.kt             # @Embedded HabitEntity + @Relation logs (calendar/detail)
│   └── migrations/                      # empty package + comment: real migrations only,
│                                        #   no fallbackToDestructiveMigration (arch assumption 8)
├── data/repository/
│   ├── HabitRepository.kt
│   ├── HabitLogRepository.kt
│   └── AppStateRepository.kt
└── domain/model/
    ├── DayStatus.kt                     # enum lives HERE (pure Kotlin) so domain can use it;
    │                                    #   entity fields are typed DayStatus via Converter
    ├── Habit.kt                         # pure model mapped from HabitEntity
    └── LogEntry.kt                      # pure model mapped from HabitLogEntity

app/schemas/com.example.tracko/.../TrackoDatabase/1.json   # generated, committed
app/src/androidTest/java/com/example/tracko/data/local/
│   ├── DbConstraintTest.kt              # UNIQUE, CHECK ranges, status set, cascade, single-row
│   └── ConverterTest.kt                 # round-trip LocalDate/DayStatus
app/src/test/java/com/example/tracko/     # empty this slice: no unit tests yet
```

## Schema rules (from data model §2 and §4.13)

- **`habit`**: `id` PK auto, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `reminder_minutes` INTEGER CHECK 0..1439, `active_days_mask` INTEGER CHECK 1..127, `prev_active_days_mask` INTEGER nullable, `active_days_effective_on` TEXT nullable, `freeze_count` INTEGER CHECK 0..3 default 0, `freeze_threshold_pct` INTEGER CHECK 50..100 default 80, `created_on` TEXT NOT NULL, `paused_since` TEXT nullable, `last_judged_week_start` TEXT nullable, `last_milestone` INTEGER CHECK in (0,50,100,150) default 0.
- **`habit_log`**: `id` PK auto, `habit_id` INTEGER NOT NULL FK → `habit(id)` **ON DELETE CASCADE**, `date` TEXT NOT NULL, `status` TEXT NOT NULL CHECK in (the 8 `DayStatus` values), `logged_at` INTEGER nullable; **UNIQUE(`habit_id`,`date`)**.
- **`app_state`**: `id` INTEGER PK (not auto) = 1, `last_finalized_date` TEXT nullable, `overall_last_milestone` INTEGER default 0.
- **Decision (approved 2026-10-07, revised):** Room 2.7.1 has **no `@Check` annotation** (verified against the `room-common-jvm-2.7.1.jar` class list), so CHECK constraints cannot be declared. Enforcement split as follows:
  - **In the DB (Room-native):** NOT NULL (Kotlin non-null types), `UNIQUE(habit_id, date)` (unique index), `ON DELETE CASCADE` (`@ForeignKey`).
  - **Via types:** `status` is `DayStatus` and `type` is `HabitType` enums — unknown values throw in the converter, so they cannot pass through the write path.
  - **In the repository (`require(...)`, task 6) + JVM unit tests:** `freezeCount` 0..3, `freezeThresholdPct` 50..100, `activeDaysMask` 1..127, `reminderMinutes` 0..1439, `lastMilestone` ∈ {0,50,100,150}.
  - Accepted tradeoff: guards protect only code that goes through repositories — allowed because the architecture says only repositories write rows.
- **Decision (approved):** `exportSchema = true`, `room.schemaLocation = app/schemas/`, schema JSON **committed**.
- **Decision (approved):** single-row `app_state` guarded by PK `id = 1`.
- Dates stored as plain local `YYYY-MM-DD` strings — no time-zone data (§4.10).

## Database & DI (per `OrderManagementCake` pattern)

```kotlin
@TypeConverters(Converter::class)
@Database(entities = [HabitEntity::class, HabitLogEntity::class, AppStateEntity::class],
           version = 1, exportSchema = true)
abstract class TrackoDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        @Volatile private var INSTANCE: TrackoDatabase? = null
        fun getInstance(context: Context): TrackoDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext,
                    TrackoDatabase::class.java, "tracko_db")
                    .build()          // NO fallbackToDestructiveMigration
                    .also { INSTANCE = it }
            }
    }
}
```

`MainActivity` wiring (repository constructors take DAOs, factories take repositories — same as your order-management app):

```kotlin
val db = TrackoDatabase.getInstance(this)
val habitRepo = HabitRepository(db.habitDao())
val logRepo = HabitLogRepository(db.habitLogDao())
val appStateRepo = AppStateRepository(db.appStateDao())
```

## Repository layer

- `HabitRepository(HabitDao)`: `observeHabits(): Flow<List<Habit>>`, `getHabit(id)`, `insert`/`update`/`delete(id)` (delete relies on cascade = FR-4), `pause(id, today)` / `resume(id, today)` writing `paused_since` (§4.8), `editActiveDays(...)` — **stubbed with the finalize-then-edit transaction signature** (§4.12); body completes in `habit-edit`/`streak-engine`, but it must already be the only place that mutates the mask fields.
- `HabitLogRepository(HabitLogDao)`: `observeLogs(habitId): Flow<List<LogEntry>>`, `upsert(...)`, `logsInRange(from, to)`. Row construction forbidden outside the repository (layer contract).
- `AppStateRepository(AppStateDao)`: `observeState()`, `updateLastFinalizedDate(date)`, `updateOverallMilestone(value)`.
- `HabitLogRepository.upsert` may guard date ∈ {today, yesterday} and ≥ `created_on` as a *repository call guard* — full `LoggingRules` is slice 2; do not duplicate its logic now.

## Code Style

Follow `docs/architecture.md` "Canonical code style": entities `XxxEntity` with primitives/snake_case `@ColumnInfo` (your `OrderEntity` style), enums in `domain/model`, mapping `XxxEntity → Xxx` via constructors in the repository/model.

```kotlin
@Entity(
    tableName = "habit_log",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habit_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["habit_id", "date"], unique = true)],
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "status") val status: DayStatus,
    @ColumnInfo(name = "logged_at") val loggedAt: Long?,
)
```

Note: `DayStatus` is imported from `domain/model` — direction `data → domain` only; `domain` never imports Room or Android.

## Testing Strategy

- **Instrumented (`data/local/` package):**
  1. Two logs with same `(habit_id, date)` → second rejected.
  2. Delete habit → all its logs gone (cascade).
  3. Enum safety: unknown status/type strings throw in converter round-trip (`DayStatus.valueOf` rejects); repository range guards covered by JVM unit tests in task 6 (see revised constraint decision above).
  4. Converter round-trips: `2026-10-07` ↔ `LocalDate`, all 8 `DayStatus` values.
  5. `app_state` second row rejected (single-row assumption guarded).
- **Unit:** none required this slice (domain logic = slice 2). Keep `ExampleUnitTest` untouched.
- **Verify:** `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`.

## Boundaries

- **Always:** schema matches §2 exactly (any mismatch → stop and flag, don't improvise); version pinning verified by real build output; schema JSON committed; `HabitWithLogs` relation lives in `relations/`.
- **Ask first:** anything requiring a column not in §2, a Room version bump, or a migration.
- **Never:** `fallbackToDestructiveMigration()`; write `PAUSED`/`NOT_ACTIVE` helpers into the repository yet; add DI frameworks; create `domain/` calculation classes (slice 2); call `getInstance` from a screen (wiring stays in `MainActivity`).

## Success Criteria

- [x] Dependencies resolve and `assembleDebug` passes with the versions recorded above (task 1, done 2026-10-07)
- [x] 3 entities + `Converter` + `HabitWithLogs` exist; columns/contracts match the §2 checklist (entities + `Converter` done 2026-10-08; `HabitWithLogs` done 2026-10-09 — schema JSON verified column-for-column against §2)
- [x] `domain/model/DayStatus.kt` + `Habit.kt` + `LogEntry.kt` exist, Android-free (task 2, done 2026-10-07; also `HabitType.kt`)
- [ ] `app/schemas/.../TrackoDatabase/1.json` exists and is committed (generated and committed 2026-10-09)
- [ ] All 5 instrumented test groups pass on device/emulator
- [x] `./gradlew test` and `./gradlew lint` green (verified 2026-10-09 alongside `assembleDebug`)
- [ ] `MainActivity` builds repos + passes them to factories (no `AppContainer`/`Application`)

## Tasks (ordered, ≤5 files each)

1. **Deps** — ✅ **DONE 2026-10-07**: `libs.versions.toml` + `app/build.gradle.kts` (KSP, Room 2.7.1, desugaring, coroutines-test, lifecycle-compose); `assembleDebug`, `test`, `lint` all green.
2. **Domain models + enum** — ✅ **DONE 2026-10-07**: `DayStatus`, `HabitType`, `Habit`, `LogEntry` (see sub-spec above). *Verify: build.*
3. **Entities + converter** — ✅ **DONE 2026-10-08**: `HabitEntity`, `HabitLogEntity`, `AppStateEntity`, `Converter` (sub-spec above). *Verify: build.*
4. **Database + relations** — ✅ **DONE 2026-10-09**: `TrackoDatabase` (singleton, `exportSchema = true`, `@TypeConverters(Converter)`), `relations/HabitWithLogs.kt`, schema location via the `room.schemaLocation` KSP arg (already present from task 1). Build generated `app/schemas/com.example.tracko.data.local.TrackoDatabase/1.json` — verified against §2 (13 cols / 5 cols + FK CASCADE + UNIQUE index / 3 cols, PK `autoGenerate = false`); **pending commit**.
5. **DAOs** — ✅ **DONE 2026-10-09**: `HabitDao`, `HabitLogDao`, `AppStateDao` — pulled forward into task 4 because `@Database` references all three and cannot compile without them. *Verify: build (green, same run).*
6. **Repositories** — `HabitRepository`, `HabitLogRepository`, `AppStateRepository`. *Verify: build.*
7. **DI wiring** — `MainActivity` singleton → repos → existing/new factories. *Verify: app launches.*
8. **Tests** — `DbConstraintTest`, `ConverterTest`. *Verify: `connectedAndroidTest` + `test` + `lint` green.*

## Sub-spec: Task 2 — Domain models (accepted 2026-10-07)

**Objective:** create the pure-Kotlin vocabulary the streak engine and UI will work in — the *meaning* versions of the three tables, with zero Android. Nothing here computes anything yet.
**Verify:** `./gradlew assembleDebug` + `./gradlew test` + `./gradlew lint` (no logic → no unit tests this task)

### Files (4, all new)

```
app/src/main/java/com/example/tracko/domain/model/
├── DayStatus.kt      # enum — the 8 log statuses from data model §2.2
├── HabitType.kt      # enum — POSITIVE | NEGATIVE (replaces the raw strings)
├── Habit.kt          # data class — mirrors the `habit` table
└── LogEntry.kt       # data class — mirrors one `habit_log` row
```

### Shapes

```kotlin
enum class DayStatus { DONE, CLEAN, SLIPPED, MISSED, FROZEN, NOT_ACTIVE, PAUSED, BONUS }

enum class HabitType { POSITIVE, NEGATIVE }

data class Habit(
    val id: Long = 0,
    val name: String,
    val type: HabitType,
    val reminderMinutes: Int,
    val activeDaysMask: Int,
    val prevActiveDaysMask: Int? = null,
    val activeDaysEffectiveOn: LocalDate? = null,
    val freezeCount: Int = 0,
    val freezeThresholdPct: Int = 80,
    val createdOn: LocalDate,
    val pausedSince: LocalDate? = null,
    val lastJudgedWeekStart: LocalDate? = null,
    val lastMilestone: Int = 0,
)

data class LogEntry(
    val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val status: DayStatus,
    val loggedAt: Long? = null,   // null = system wrote it (matters for §4.7)
)
```

### Rules

- **Zero `android.*`/`androidx.*` imports.** Only `java.time.LocalDate` (core Java, desugared at build level — not an Android dependency).
- **Dates are `LocalDate` here, not String** — Room stores `YYYY-MM-DD` (converter, task 4); the domain uses the real type so date math stays real.
- **No helper methods yet** (no `isActiveOn(...)`) — add when `streak-engine` needs them. Prevents speculative code.
- **Immutable `data class`es with defaults matching data model** (freeze 0, threshold 80, milestone 0).
- **`activeDaysMask` stays `Int`** — the `Set<DayOfWeek>` converter idea (§4.4) is deferred until logic demands it.

### Acceptance criteria

- [ ] 4 files under `domain/model/`, all compile
- [ ] No `android.`/`androidx.` imports anywhere under `domain/`
- [ ] Fields/names trace 1:1 to data model §2
- [ ] `assembleDebug` + `test` + `lint` green
- [ ] Diff shows only data classes + enums (no logic)

### Boundary

- **Never in this task:** Room imports, entity↔domain mapping, converters, helper methods, unit tests beyond compiling.

## Sub-spec: Task 3 — Entities + Converter (accepted 2026-10-07)

**Objective:** the storage twins of the domain models — Room-annotated entities that map 1:1 to data model §2, plus the `Converter` that translates domain types (`LocalDate`, enums) into what SQLite stores (TEXT).
**Verify:** `./gradlew assembleDebug` + `./gradlew test` + `./gradlew lint`

### Files (4, all new)

```
app/src/main/java/com/example/tracko/data/local/
├── Converter.kt                     # LocalDate ↔ "YYYY-MM-DD", DayStatus ↔ String, HabitType ↔ String
└── entities/
    ├── HabitEntity.kt               # table `habit`
    ├── HabitLogEntity.kt            # table `habit_log` (FK cascade + unique index)
    └── AppStateEntity.kt            # table `app_state` (id = 1, not auto)
```

### Shapes (column names exactly per §2)

```kotlin
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                                   // NOT NULL
    val type: HabitType,                                // enum → TEXT "POSITIVE"/"NEGATIVE"
    @ColumnInfo(name = "reminder_minutes") val reminderMinutes: Int,
    @ColumnInfo(name = "active_days_mask") val activeDaysMask: Int,
    @ColumnInfo(name = "prev_active_days_mask") val prevActiveDaysMask: Int?,
    @ColumnInfo(name = "active_days_effective_on") val activeDaysEffectiveOn: LocalDate?,
    @ColumnInfo(name = "freeze_count") val freezeCount: Int = 0,
    @ColumnInfo(name = "freeze_threshold_pct") val freezeThresholdPct: Int = 80,
    @ColumnInfo(name = "created_on") val createdOn: LocalDate,
    @ColumnInfo(name = "paused_since") val pausedSince: LocalDate?,
    @ColumnInfo(name = "last_judged_week_start") val lastJudgedWeekStart: LocalDate?,
    @ColumnInfo(name = "last_milestone") val lastMilestone: Int = 0,
)

@Entity(
    tableName = "habit_log",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habit_id"],
        onDelete = ForeignKey.CASCADE,           // FR-4: delete wipes history
    )],
    indices = [Index(value = ["habit_id", "date"], unique = true)],  // one row per habit per day
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    val date: LocalDate,                            // → TEXT "YYYY-MM-DD"
    val status: DayStatus,                          // → TEXT, enum-safe
    @ColumnInfo(name = "logged_at") val loggedAt: Long?,
)

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Long = 1,                   // single row — NOT autoGenerate
    @ColumnInfo(name = "last_finalized_date") val lastFinalizedDate: LocalDate? = null,
    @ColumnInfo(name = "overall_last_milestone") val overallLastMilestone: Int = 0,
)
```

### Rules

- **Column names exactly match §2** (`snake_case` via `@ColumnInfo`) — the schema JSON must read like the data model.
- **Types via converter, not String:** fields use `LocalDate` / `DayStatus` / `HabitType`; `Converter` maps them to TEXT. An unknown status string throws in `valueOf()` — that *is* the status enforcement (see revised constraint decision).
- **`data → domain` only:** entities import `domain/model` enums; `domain/` never imports entities.
- **No logic in entities** — fields and annotations only; no `toDomain()` mappers here (mappers live with the repository, task 6).
- `Converter` is *not* registered yet — `@TypeConverters(Converter::class)` goes on `TrackoDatabase` in task 4.

### Acceptance criteria

- [x] 4 files under `data/local/`, compile (done 2026-10-08 — files were NUL-corrupted, rewritten from Shapes; `assembleDebug` + `test` + `lint` green)
- [x] Every column name/nullable/default traces 1:1 to §2 table
- [x] `grep "import com.example.tracko.domain" domain/` → empty (no reverse dependency)
- [x] `assembleDebug` + `test` + `lint` green
- [x] Diff shows only entities + converter (no DAOs, no database class)

### Boundary

- **Never in this task:** `TrackoDatabase`, DAOs, relations, repositories, `@TypeConverters` registration, unit tests beyond compiling.

## Open Questions

- ~~`type` CHECK constraint~~ — **decided 2026-10-07**: Room has no `@Check`; `type`/`status` are enforced by enum converters, numeric ranges by repository `require(...)` + unit tests (see the revised constraint decision in Schema rules).
