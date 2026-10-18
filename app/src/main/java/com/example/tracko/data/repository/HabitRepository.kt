package com.example.tracko.data.repository

import com.example.tracko.data.local.dao.AppStateDao
import com.example.tracko.data.local.dao.HabitDao
import com.example.tracko.data.local.dao.HabitLogDao
import com.example.tracko.data.local.entities.AppStateEntity
import com.example.tracko.data.local.entities.HabitEntity
import com.example.tracko.data.local.entities.HabitLogEntity
import com.example.tracko.domain.Finalizer
import com.example.tracko.domain.WeeklyFreezeJudge
import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.LogEntry
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The only way to write `habit` rows (architecture layer contract).
 * Screens/ViewModels never touch DAOs directly.
 *
 * Why the `require(...)` guards: Room 2.7.1 has no `@Check`, so ranges from
 * data model §2.1 (FR-1, FR-11..13, FR-28, FR-25) are enforced here + covered
 * by JVM unit tests. Anything that skips this class skips the guards.
 *
 * This repository also owns the finalize-then-act transactions (§4.12): the
 * pure `Finalizer` decides, this class executes (writes rows, counters,
 * bookmark) and applies `WeeklyFreezeJudge` verdicts for finalized Sundays.
 */
class HabitRepository(
    private val dao: HabitDao,
    private val logDao: HabitLogDao,
    private val appStateDao: AppStateDao,
) {
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
     * Resume: backfill `PAUSED` for each day in `[pausedSince, yesterday]` that
     * has no row, then clear the flag (§4.8). Finalized days already have rows
     * so they are naturally skipped; a `DONE` row is never overwritten (§7.11).
     */
    suspend fun resume(id: Long, today: LocalDate) {
        val current = dao.getHabit(id) ?: return
        val since = current.pausedSince ?: return
        val yesterday = today.minusDays(1)
        var day = since
        while (!day.isAfter(yesterday)) {
            val hasRow = logDao.logsInRange(day, day).any { it.habitId == id }
            if (!hasRow) {
                logDao.upsert(HabitLogEntity(habitId = id, date = day, status = DayStatus.PAUSED, loggedAt = null))
            }
            day = day.plusDays(1)
        }
        dao.update(current.copy(pausedSince = null))
    }

    /**
     * Change active days (FR-32, §4.12): finalize first (at most yesterday +
     * today stay pending), then remember the old rule unless already edited
     * today, store the new mask, and date the edit to [today]. The only place
     * that mutates the mask fields.
     */
    suspend fun editActiveDays(id: Long, newMask: Int, today: LocalDate) {
        require(newMask in 1..127) { "activeDaysMask must be 1..127" }
        val current = dao.getHabit(id) ?: return
        finalizeHabit(current.toDomain(), today)
        val fresh = dao.getHabit(id) ?: return
        val prev = if (fresh.activeDaysEffectiveOn != today) fresh.activeDaysMask else fresh.prevActiveDaysMask
        val updated = fresh.copy(
            activeDaysMask = newMask,
            prevActiveDaysMask = prev,
            activeDaysEffectiveOn = today,
        )
        validate(updated.toDomain())
        dao.update(updated)
    }

    /**
     * Open-time finalization (§4.6, FR-19): judge every habit once, then the
     * UI may read. Loop only — per-habit work stays in [finalizeHabit].
     */
    suspend fun finalizeAll(today: LocalDate) {
        dao.observeHabits().first().forEach { finalizeHabit(it.toDomain(), today) }
    }

    /**
     * Runs finalization for one habit and persists everything: plan rows,
     * freeze/milestone counters, weekly verdicts for finalized Sundays, and
     * the global bookmark. Pure `Finalizer` decides; this executes.
     */
    suspend fun finalizeHabit(habit: Habit, today: LocalDate) {
        val state = appStateDao.getState()
        val existing = logDao.logsInRange(habit.createdOn, today).filter { it.habitId == habit.id }
        val plan = Finalizer().finalize(habit, existing.map { it.toDomain() }, state?.lastFinalizedDate, today)
        plan.logsToWrite.forEach { logDao.upsert(it.toEntity()) }

        var updated = habit.copy(freezeCount = plan.freezeCount, lastMilestone = plan.lastMilestone)
        // Judge each Sunday finalized in this run (Monday–Sunday weeks, §4.7).
        val rangeStart = maxOf(habit.createdOn, state?.lastFinalizedDate?.plusDays(1) ?: habit.createdOn)
        val rangeEnd = today.minusDays(2)
        val allLogs = (existing.map { it.toDomain() } + plan.logsToWrite).associateBy { it.date }
        var day = rangeStart
        while (!day.isAfter(rangeEnd)) {
            if (day.dayOfWeek == DayOfWeek.SUNDAY) {
                val monday = day.minusDays(6)
                val weekLogs = (0..6).mapNotNull { allLogs[monday.plusDays(it.toLong())] }
                val verdict = WeeklyFreezeJudge().judgeWeek(updated, weekLogs, monday)
                if (verdict.freezeAwarded) {
                    updated = updated.copy(freezeCount = updated.freezeCount + 1)
                }
                updated = updated.copy(lastJudgedWeekStart = verdict.lastJudgedWeekStart)
            }
            day = day.plusDays(1)
        }
        validate(updated)
        dao.update(updated.toEntity())

        val bookmark = state?.lastFinalizedDate?.let { maxOf(it, plan.lastFinalizedDate) } ?: plan.lastFinalizedDate
        appStateDao.upsert((state ?: AppStateEntity()).copy(lastFinalizedDate = bookmark))
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

    private fun LogEntry.toEntity() = HabitLogEntity(
        id = id,
        habitId = habitId,
        date = date,
        status = status,
        loggedAt = loggedAt,
    )

    private fun HabitLogEntity.toDomain() = LogEntry(
        id = id,
        habitId = habitId,
        date = date,
        status = status,
        loggedAt = loggedAt,
    )
}
