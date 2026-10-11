package com.example.tracko.data.repository

import com.example.tracko.data.local.dao.HabitDao
import com.example.tracko.data.local.entities.HabitEntity
import com.example.tracko.domain.model.Habit
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only way to write `habit` rows (architecture layer contract).
 * Screens/ViewModels never touch [HabitDao] directly.
 *
 * Why the `require(...)` guards: Room 2.7.1 has no `@Check`, so ranges from
 * data model §2.1 (FR-1, FR-11..13, FR-28, FR-25) are enforced here + covered
 * by JVM unit tests. Anything that skips this class skips the guards.
 */
class HabitRepository(private val dao: HabitDao) {
    fun observeHabits(): Flow<List<Habit>> =
        dao.observeHabits().map { list -> list.map { it.toDomain() } }

    suspend fun getHabit(id: Long): Habit? = dao.getHabit(id)?.toDomain()

    suspend fun insert(habit: Habit): Long {
        validate(habit)
        return dao.insert(habit.toEntity())
    }

    suspend fun update(habit: Habit) {
        validate(habit)
        dao.update(habit.toEntity())
    }

    /** Delete wipes history via `ON DELETE CASCADE` (FR-4). */
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /** Pause takes effect today; the value is the day the pause began (§4.8). */
    suspend fun pause(id: Long, today: LocalDate) {
        val current = dao.getHabit(id) ?: return
        if (current.pausedSince == null) {
            dao.update(current.copy(pausedSince = today))
        }
    }

    /**
     * Resume clears the flag. Backfilling `PAUSED` rows for unfinalized days
     * (§4.8, last sentence) completes with the `Finalizer` in `streak-engine` —
     * finalized days already got `PAUSED` from the routine, and a `DONE` row is
     * never overwritten.
     */
    suspend fun resume(id: Long, today: LocalDate) {
        val current = dao.getHabit(id) ?: return
        if (current.pausedSince != null) {
            // TODO(streak-engine): write PAUSED for each day in
            //   [pausedSince, yesterday] with no row, then clear the flag.
            //   Needs HabitLogDao + Finalizer, so deferred (SPEC task 6 = stub).
            dao.update(current.copy(pausedSince = null))
        }
    }

    /**
     * Change active days — STUB (FR-32, §4.12). The real body (finalize first,
     * then copy current mask to `prev_active_days_mask` + set
     * `active_days_effective_on = today`) completes in `habit-edit` /
     * `streak-engine`. This stays the only place that mutates the mask fields.
     */
    suspend fun editActiveDays(id: Long, newMask: Int, today: LocalDate) {
        require(newMask in 1..127) { "activeDaysMask must be 1..127" }
        TODO("streak-engine: finalize-then-edit transaction (§4.12)")
    }

    private fun validate(habit: Habit) {
        require(habit.activeDaysMask in 1..127) { "activeDaysMask must be 1..127 (FR-1)" }
        require(habit.reminderMinutes in 0..1439) { "reminderMinutes must be 0..1439 (FR-15)" }
        require(habit.freezeCount in 0..3) { "freezeCount must be 0..3 (FR-11..13)" }
        require(habit.freezeThresholdPct in 50..100) { "freezeThresholdPct must be 50..100 (FR-28)" }
        require(habit.lastMilestone in setOf(0, 50, 100, 150)) { "lastMilestone must be 0/50/100/150 (FR-25)" }
    }

    private fun Habit.toEntity() = HabitEntity(
        id = id,
        name = name,
        type = type,
        reminderMinutes = reminderMinutes,
        activeDaysMask = activeDaysMask,
        prevActiveDaysMask = prevActiveDaysMask,
        activeDaysEffectiveOn = activeDaysEffectiveOn,
        freezeCount = freezeCount,
        freezeThresholdPct = freezeThresholdPct,
        createdOn = createdOn,
        pausedSince = pausedSince,
        lastJudgedWeekStart = lastJudgedWeekStart,
        lastMilestone = lastMilestone,
    )

    private fun HabitEntity.toDomain() = Habit(
        id = id,
        name = name,
        type = type,
        reminderMinutes = reminderMinutes,
        activeDaysMask = activeDaysMask,
        prevActiveDaysMask = prevActiveDaysMask,
        activeDaysEffectiveOn = activeDaysEffectiveOn,
        freezeCount = freezeCount,
        freezeThresholdPct = freezeThresholdPct,
        createdOn = createdOn,
        pausedSince = pausedSince,
        lastJudgedWeekStart = lastJudgedWeekStart,
        lastMilestone = lastMilestone,
    )
}
