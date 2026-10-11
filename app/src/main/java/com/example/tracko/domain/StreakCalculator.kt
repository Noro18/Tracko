package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import com.example.tracko.domain.model.LogEntry
import java.time.LocalDate

/** Both streak numbers for one habit, computed in a single call. */
data class StreakResult(val current: Int, val longest: Int)

/**
 * Derives streaks from logs — nothing is stored (data model §3; FR-8, FR-9,
 * FR-10, FR-26, FR-27). Pure Kotlin: in-memory inputs, `today` passed in so
 * tests can pretend any date (NFR-6). Never reads DAOs or the clock.
 *
 * Option B (SPEC-streak-engine Task 2): [currentStreak] and [longestStreak] are
 * two independent loops sharing the same §3 step-table. If the step-table ever
 * changes, edit BOTH loops together — the test suite guards both paths.
 */
class StreakCalculator {
    /** Convenience: both numbers at once. */
    fun streaks(habit: Habit, logs: List<LogEntry>, today: LocalDate): StreakResult =
        StreakResult(currentStreak(habit, logs, today), longestStreak(habit, logs, today))

    /**
     * Walk back from [today]. `DONE`/`BONUS`/`CLEAN` +1, `FROZEN` alive +0,
     * `NOT_ACTIVE`/`PAUSED`/pending skipped, `MISSED`/`SLIPPED` stop the walk.
     * A pending *scheduled* day on a negative habit counts provisionally +1
     * (§3 display rule: silence keeps the counter running).
     */
    fun currentStreak(habit: Habit, logs: List<LogEntry>, today: LocalDate): Int {
        // NOTE: keep step-table in sync with longestStreak below.
        val byDate = logs.associateBy { it.date }
        var count = 0
        var day = today
        while (!day.isBefore(habit.createdOn)) {
            val entry = byDate[day]
            if (entry == null) {
                // Pending day: provisionally clean only for scheduled negatives.
                if (habit.type == HabitType.NEGATIVE && isActiveOn(habit, day)) count++
                day = day.minusDays(1)
                continue
            }
            when (entry.status) {
                DayStatus.DONE, DayStatus.BONUS, DayStatus.CLEAN -> count++
                DayStatus.FROZEN -> { /* alive, adds nothing */ }
                DayStatus.NOT_ACTIVE, DayStatus.PAUSED -> { /* skipped */ }
                DayStatus.MISSED, DayStatus.SLIPPED -> break
            }
            day = day.minusDays(1)
        }
        return count
    }

    /**
     * Best run anywhere in history, using the same §3 step-table as
     * [currentStreak] (a stop there is a run-reset here). Forward scan from
     * `createdOn` to [today], keeping the best run.
     */
    fun longestStreak(habit: Habit, logs: List<LogEntry>, today: LocalDate): Int {
        // NOTE: keep step-table in sync with currentStreak above.
        val byDate = logs.associateBy { it.date }
        var best = 0
        var run = 0
        var day = habit.createdOn
        while (!day.isAfter(today)) {
            val entry = byDate[day]
            if (entry == null) {
                // Pending day: provisionally clean only for scheduled negatives.
                if (habit.type == HabitType.NEGATIVE && isActiveOn(habit, day)) {
                    run++
                    if (run > best) best = run
                }
                day = day.plusDays(1)
                continue
            }
            when (entry.status) {
                DayStatus.DONE, DayStatus.BONUS, DayStatus.CLEAN -> {
                    run++
                    if (run > best) best = run
                }
                DayStatus.FROZEN -> { /* alive, adds nothing */ }
                DayStatus.NOT_ACTIVE, DayStatus.PAUSED -> { /* skipped */ }
                DayStatus.MISSED, DayStatus.SLIPPED -> run = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    /**
     * Overall daily streak over positive habits only (§3). Per date: completed
     * = any positive `DONE`/`BONUS`; scheduled = any positive
     * `DONE`/`MISSED`/`FROZEN`. Completed +1, scheduled-but-not-completed
     * breaks, anything else (unscheduled or pending) skips.
     */
    fun overallStreak(habits: List<Habit>, allLogs: List<LogEntry>, today: LocalDate): Int {
        val positives = habits.filter { it.type == HabitType.POSITIVE }
        if (positives.isEmpty()) return 0
        val positiveIds = positives.map { it.id }.toSet()
        val start = positives.minOf { it.createdOn }
        val byDate = allLogs
            .filter { it.habitId in positiveIds }
            .groupBy { it.date }
        var count = 0
        var day = today
        while (!day.isBefore(start)) {
            val statuses = byDate[day].orEmpty().map { it.status }.toSet()
            val completed = statuses.any { it == DayStatus.DONE || it == DayStatus.BONUS }
            if (completed) {
                count++
            } else {
                val scheduled = statuses.any {
                    it == DayStatus.DONE || it == DayStatus.MISSED || it == DayStatus.FROZEN
                }
                if (scheduled) break
                // Else: nothing scheduled, or pending day with no rows — skip.
            }
            day = day.minusDays(1)
        }
        return count
    }
}
