package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import com.example.tracko.domain.model.LogEntry
import java.time.LocalDate

/**
 * What the repository must write after finalization: the new rows plus the
 * updated counters and bookmark. The repository executes the plan; the
 * [Finalizer] only decides it (layer contract — logic stays JVM-testable).
 */
data class FinalizePlan(
    val logsToWrite: List<LogEntry>,
    val freezeCount: Int,
    val lastMilestone: Int,
    val lastFinalizedDate: LocalDate,
)

/**
 * The night-shift clerk (data model §4.6; FR-19, FR-25, FR-30). Inks old diary
 * days in order, oldest first. Pure Kotlin: in-memory inputs, `today` passed
 * in (NFR-6). Never touches DAOs or the clock.
 *
 * Per habit, per date D from `max(lastFinalized + 1, createdOn)` while
 * `D <= today - 2` (yesterday stays editable, FR-19):
 * 1. Row exists → keep it. Only `SLIPPED` may become `FROZEN` (spending one
 *    freeze); an unfrozen slip resets `lastMilestone`. Never overwrite.
 * 2. Paused on D → `PAUSED`.
 * 3. Weekday not scheduled under the mask in force on D → `NOT_ACTIVE`.
 * 4. Scheduled, no answer → positive: `FROZEN` (-1) or `MISSED` (+ reset);
 *    negative: silent `CLEAN` (`loggedAt = null`, earns no freezes).
 */
class Finalizer {
    fun finalize(
        habit: Habit,
        existing: List<LogEntry>,
        lastFinalized: LocalDate?,
        today: LocalDate,
    ): FinalizePlan {
        val byDate = existing.associateBy { it.date }
        val start = maxOf(habit.createdOn, lastFinalized?.plusDays(1) ?: habit.createdOn)
        val end = today.minusDays(2)
        var freezes = habit.freezeCount
        var milestone = habit.lastMilestone
        val writes = mutableListOf<LogEntry>()

        var day = start
        while (!day.isAfter(end)) {
            val entry = byDate[day]
            if (entry != null) {
                // Step 1: existing row wins. Only a covered slip changes.
                if (entry.status == DayStatus.SLIPPED) {
                    if (freezes > 0) {
                        writes.add(entry.copy(status = DayStatus.FROZEN))
                        freezes--
                    } else {
                        milestone = 0
                    }
                }
            } else if (habit.pausedSince != null && !day.isBefore(habit.pausedSince)) {
                // Step 2: paused.
                writes.add(systemRow(habit, day, DayStatus.PAUSED))
            } else if (!isActiveOn(habit, day)) {
                // Step 3: not scheduled under the rule in force on D.
                writes.add(systemRow(habit, day, DayStatus.NOT_ACTIVE))
            } else if (habit.type == HabitType.NEGATIVE) {
                // Step 4b: silent CLEAN — streak runs, freezes need answers.
                writes.add(systemRow(habit, day, DayStatus.CLEAN))
            } else if (freezes > 0) {
                // Step 4a: covered miss.
                writes.add(systemRow(habit, day, DayStatus.FROZEN))
                freezes--
            } else {
                // Step 4a: real miss.
                writes.add(systemRow(habit, day, DayStatus.MISSED))
                milestone = 0
            }
            day = day.plusDays(1)
        }

        val bookmark = if (start.isAfter(end)) {
            lastFinalized ?: start.minusDays(1)
        } else {
            end
        }
        return FinalizePlan(writes, freezes, milestone, bookmark)
    }

    /** A system-written row: no tap timestamp (`loggedAt = null`). */
    private fun systemRow(habit: Habit, date: LocalDate, status: DayStatus) = LogEntry(
        habitId = habit.id,
        date = date,
        status = status,
        loggedAt = null,
    )
}
