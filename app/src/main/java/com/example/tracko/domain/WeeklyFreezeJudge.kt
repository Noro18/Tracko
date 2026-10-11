package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.LogEntry
import java.time.LocalDate

/**
 * The verdict for one judged week: whether a freeze was earned, and the stamp
 * proving the week was judged (even when skipped — see §4.5).
 */
data class WeekJudgment(val freezeAwarded: Boolean, val lastJudgedWeekStart: LocalDate)

/**
 * Grades one Monday–Sunday week per habit (data model §4.5, §4.7; FR-11–FR-14,
 * FR-28, FR-30). Pure Kotlin: in-memory inputs, no DAOs, no clock (NFR-6).
 *
 * Self-guarding: an already-judged week is a no-op, so the §4.5 "never twice"
 * guarantee holds no matter who calls (app-open path, worker path, tests).
 * Integer percent math throughout — no float rounding surprises.
 */
class WeeklyFreezeJudge {
    fun judgeWeek(habit: Habit, weekLogs: List<LogEntry>, weekMonday: LocalDate): WeekJudgment {
        // §4.5: never judge the same week twice.
        val already = habit.lastJudgedWeekStart
        if (already != null && !already.isBefore(weekMonday)) {
            return WeekJudgment(false, already)
        }
        // Step 1: skip partial weeks (FR-30) — still stamped, never re-graded.
        if (habit.createdOn.isAfter(weekMonday)) {
            return WeekJudgment(false, weekMonday)
        }
        if (weekLogs.any { it.status == DayStatus.PAUSED }) {
            return WeekJudgment(false, weekMonday)
        }
        // Step 2: countable days. Zero → skip quietly, no division (§7.12).
        val countable = weekLogs.filter {
            it.status != DayStatus.NOT_ACTIVE &&
                it.status != DayStatus.PAUSED &&
                it.status != DayStatus.BONUS
        }
        if (countable.isEmpty()) {
            return WeekJudgment(false, weekMonday)
        }
        // Step 3: answered days only — silent CLEANs earn nothing (§4.7 fix).
        val answered = countable.count {
            it.status == DayStatus.DONE ||
                (it.status == DayStatus.CLEAN && it.loggedAt != null)
        }
        // Step 4: whole-number rate against the habit's own bar, cap of 3.
        val awarded = answered * 100 >= habit.freezeThresholdPct * countable.size &&
            habit.freezeCount < 3
        // Step 5: always stamp.
        return WeekJudgment(awarded, weekMonday)
    }
}
