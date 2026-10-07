# Spec: `data-core` (Module 1 of 8)

**Status:** DRAFT — awaiting human approval.
**Author:** opencode | **Date:** 2026-10-07
**Parent:** `docs/architecture.md` (approved) → capability map module `data-core`
**Implements:** storage half of data model v1.1 (§2, §4.13); no behavior yet

---

## Objective

Persist the three tables exactly as specified in the data model, expose them through Flow-based DAOs and repositories, and wire everything with manual DI. No UI, no streak/domain logic — that is slice 2 (`streak-engine`).

**Done means:** the schema matches data model §2 column-for-column with DB-enforced constraints, the schema JSON is committed to VCS, repositories are the only write path, and `./gradlew test` + `./gradlew lint` + `assembleDebug` are green.

## Tech Stack (changes)

| Addition | Version (candidate — verify in task 1 with a clean build) |
|---|---|
| KSP plugin (`com.google.devtools.ksp`) | `2.0.21-1.0.28` (matches Kotlin 2.0.21) |
| `androidx.room:room-runtime`, `room-ktx`, `room-testing` | `2.6.1` |
| `androidx.room:room-compiler` (KSP) | `2.6.1` |
| `com.android.tools:desugar_jdk_libs` (core library desugaring) | `2.1.4` |
| `kotlinx-coroutines-test` | `1.9.0` |
| `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | `2.10.0` (match existing lifecycle) |

All go in `gradle/libs.versions.toml`; `app/build.gradle.kts` gains `ksp(libs.androidx.room.compiler)`, `coreLibraryDesugaring(libs.desugar.jdk.libs)`, `isCoreLibraryDesugaringEnabled = true`, and Room build features. If any candidate version fails to resolve or build, downgrade/upgrade minimally and record the final version in this spec.

## Project Structure (files created)

```
app/src/main/java/com/example/tracko/
├── TrackoApplication.kt              # owns AppContainer; registered in AndroidManifest
├── AppContainer.kt                   # manual DI: db → dao → repository
├── core/data/db/
│   ├── TrackoDatabase.kt             # @Database(version = 1, exportSchema = true)
│   ├── HabitEntity.kt                # table `habit`
│   ├── HabitLogEntity.kt             # table `habit_log`
│   ├── AppStateEntity.kt             # table `app_state` (single row, id = 1)
│   ├── DayStatus.kt                  # enum: DONE, CLEAN, SLIPPED, MISSED, FROZEN, NOT_ACTIVE, PAUSED, BONUS
│   ├── Converters.kt                 # LocalDate ↔ "YYYY-MM-DD", DayStatus ↔ String
│   ├── HabitDao.kt, HabitLogDao.kt, AppStateDao.kt
app/src/main/AndroidManifest.xml      # android:name=".TrackoApplication"
app/schemas/<pkg>/TrackoDatabase/1.json   # generated, committed
app/src/androidTest/java/com/example/tracko/db/
│   ├── DbConstraintTest.kt           # UNIQUE, CHECK ranges, status set, cascade delete
│   └── ConverterTest.kt              # round-trip LocalDate/DayStatus
app/src/test/java/com/example/tracko/    # empty slice: no unit tests yet
```

## Schema rules (from data model §2 and §4.13)

- **`habit`**: `id` PK auto, `name` TEXT NOT NULL, `type` TEXT NOT NULL (`POSITIVE`/`NEGATIVE`), `reminder_minutes` INTEGER CHECK 0..1439, `active_days_mask` INTEGER CHECK 1..127, `prev_active_days_mask` INTEGER nullable, `active_days_effective_on` TEXT nullable, `freeze_count` INTEGER CHECK 0..3 default 0, `freeze_threshold_pct` INTEGER CHECK 50..100 default 80, `created_on` TEXT NOT NULL, `paused_since` TEXT nullable, `last_judged_week_start` TEXT nullable, `last_milestone` INTEGER CHECK in (0,50,100,150) default 0.
- **`habit_log`**: `id` PK auto, `habit_id` INTEGER NOT NULL FK → `habit(id)` **ON DELETE CASCADE**, `date` TEXT NOT NULL, `status` TEXT NOT NULL CHECK in (the 8 `DayStatus` values), `logged_at` INTEGER nullable; **UNIQUE(`habit_id`,`date`)**.
- **`app_state`**: `id` INTEGER PK (not auto) = 1, `last_finalized_date` TEXT nullable, `overall_last_milestone` INTEGER default 0.
- **Decision (approved):** constraints are enforced **in the DB** (Room `@Entity` annotations / CHECK where Room requires it) and asserted by instrumented tests — not merely validated in app code.
- **Decision (approved):** `exportSchema = true`, `room.schemaLocation = app/schemas/`, schema JSON **committed**.
- Dates stored as plain local `YYYY-MM-DD` strings — no time-zone data (§4.10).

## Repository layer

- `HabitRepository`: `observeHabits(): Flow<List<Habit>>`, `getHabit(id)`, `insert`/`update`/`delete(id)` (delete relies on cascade = FR-4), `pause(id, today)` / `resume(id, today)` writing `paused_since` (§4.8), `editActiveDays(...)` — **stubbed with the finalize-then-edit transaction signature** (§4.12); its body is completed in `habit-edit`/`streak-engine`, but it must already be the only place that mutates the mask fields.
- `LogRepository`: `observeLogs(habitId): Flow<List<LogEntry>>`, `upsert(...)`, `logsInRange(from, to)`. Row construction is forbidden outside the repository (layer contract).
- `AppStateRepository`: `observeState()`, `updateLastFinalizedDate(date)`, `updateOverallMilestone(value)`.
- `LogRepository.upsert` validates `date` is today/yesterday and ≥ `created_on` only as a *guard for the repo call* — full `LoggingRules` belongs to slice 2; do not duplicate its logic now.

## Code Style

Follow `docs/architecture.md` "Canonical code style": entities are `XxxEntity` with primitives/int masks, domain-facing models (`Habit`, `LogEntry`) live in `core/domain/model` and map from entities via constructors — but **note:** `core/domain/model` is created empty here except for these two data classes (pure Kotlin, no Android imports); calculation classes arrive in slice 2.

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

## Testing Strategy

- **Instrumented (`db/` package):**
  1. Insert two logs with same `(habit_id, date)` → second rejected.
  2. Delete habit → all its logs gone (cascade).
  3. `status = "NOT_A_STATUS"`, `freeze_count = 4`, `freeze_threshold_pct = 40`, `active_days_mask = 0` → each rejected.
  4. Converter round-trips: `2026-10-07` ↔ `LocalDate`, all 8 statuses.
  5. `app_state` second row rejected (single-row assumption guarded).
- **Unit:** none required this slice (domain logic = slice 2). Keep `ExampleUnitTest` untouched.
- **Verify:** `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`.

## Boundaries

- **Always:** schema matches §2 exactly (any mismatch → stop and flag, don't improvise); version pinning verified by real build output; schema JSON committed.
- **Ask first:** anything requiring a column not in §2, a Room version bump, or a migration.
- **Never:** write `PAUSED`/`NOT_ACTIVE` helpers into the repository yet; add DI frameworks; create `core/domain` calculation classes.

## Success Criteria

- [ ] Dependencies resolve and `assembleDebug` passes with pinned versions (recorded above)
- [ ] 3 entities + `DayStatus` + converters exist; columns/contracts match §2 checklist
- [ ] `app/schemas/.../1.json` exists and is committed
- [ ] All 5 instrumented test groups pass on device/emulator
- [ ] `./gradlew test` and `./gradlew lint` green
- [ ] `TrackoApplication` + `AppContainer` construct repositories; manifest updated

## Tasks (ordered, ≤5 files each)

1. **Deps** — edit `libs.versions.toml` + `app/build.gradle.kts` (KSP, Room, desugaring, coroutines-test); run `assembleDebug`; fix versions if needed; update this spec with final versions. *Verify: clean build passes.*
2. **Entities** — `DayStatus`, `Converters`, `HabitEntity`, `HabitLogEntity`, `AppStateEntity`. *Verify: build.*
3. **Database** — `TrackoDatabase` (version 1, exportSchema), `room.schemaLocation` KSP arg, constraints declared. *Verify: build generates `1.json`; commit it.*
4. **DAOs** — `HabitDao`, `HabitLogDao`, `AppStateDao`. *Verify: build.*
5. **Repositories** — `HabitRepository`, `LogRepository`, `AppStateRepository`. *Verify: build.*
6. **DI** — `AppContainer`, `TrackoApplication`, manifest entry. *Verify: app launches (assembleDebug + install).*
7. **Tests** — `DbConstraintTest`, `ConverterTest`. *Verify: `connectedAndroidTest` + `test` + `lint` green.*

## Open Questions

- None blocking. `type` CHECK constraint enforcement (POSITIVE/NEGATIVE) added if Room supports it cleanly in task 3; otherwise repository guard + test — decided in task 3 and noted here.
