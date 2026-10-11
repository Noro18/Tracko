package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import com.example.tracko.domain.model.LogEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 2 proof: §3 step-table in both loops + overall walk (§7 scenarios 1, 2,
 * 8, 9 + edges). Plain JVM, explicit `today` — no clock, no device (NFR-6).
 * Calendar note: 2026-10-11 is a Sunday (Oct 5 = Mon, Oct 9 = Fri).
 */
class StreakCalculatorTest {
    private val calc = StreakCalculator()

    private fun habit(
        id: Long = 1L,
        type: HabitType = HabitType.POSITIVE,
        activeDaysMask: Int = 127, // every day
        createdOn: LocalDate = LocalDate.parse("2026-10-01"),
    ) = Habit(
        id = id,
        name = "Run",
        type = type,
        reminderMinutes = 1290,
        activeDaysMask = activeDaysMask,
        createdOn = createdOn,
    )

    private fun log(habitId: Long, date: String, status: DayStatus) = LogEntry(
        habitId = habitId,
        date = LocalDate.parse(date),
        status = status,
        loggedAt = if (status == DayStatus.MISSED || status == DayStatus.FROZEN) null else 1L,
    )

    @Test
    fun eightDoneThenMiss_currentZeroLongestEight() {
        // §7.1: done every day for 8 days, then one miss with 0 freezes.
        val h = habit()
        val logs = (1..8).map { log(1L, "2026-10-%02d".format(it), DayStatus.DONE) } +
            log(1L, "2026-10-09", DayStatus.MISSED)
        val result = calc.streaks(h, logs, LocalDate.parse("2026-10-09"))
        assertEquals(0, result.current)
        assertEquals(8, result.longest)
    }

    @Test
    fun missWithFreeze_staysAliveAddsNothing() {
        // §7.2: FROZEN keeps the streak alive but adds no day.
        val h = habit()
        val logs = listOf(
            log(1L, "2026-10-01", DayStatus.DONE),
            log(1L, "2026-10-02", DayStatus.DONE),
            log(1L, "2026-10-03", DayStatus.DONE),
            log(1L, "2026-10-04", DayStatus.FROZEN),
            log(1L, "2026-10-05", DayStatus.DONE),
            log(1L, "2026-10-06", DayStatus.DONE),
        )
        val result = calc.streaks(h, logs, LocalDate.parse("2026-10-06"))
        assertEquals(5, result.current)
        assertEquals(5, result.longest)
    }

    @Test
    fun overallSurvivesOffDays_perfectMonWedFri() {
        // §7.8a: off-days (Tue/Thu/Sat/Sun) are skipped, not broken.
        val h = habit(activeDaysMask = 21, createdOn = LocalDate.parse("2026-10-05")) // Mon/Wed/Fri
        val logs = listOf(
            log(1L, "2026-10-05", DayStatus.DONE),
            log(1L, "2026-10-07", DayStatus.DONE),
            log(1L, "2026-10-09", DayStatus.DONE),
        )
        assertEquals(3, calc.overallStreak(listOf(h), logs, LocalDate.parse("2026-10-11")))
    }

    @Test
    fun overallBreaksOnMissedFriday() {
        // §7.8b: a scheduled-but-not-completed day breaks the overall walk.
        val h = habit(activeDaysMask = 21, createdOn = LocalDate.parse("2026-10-05")) // Mon/Wed/Fri
        val logs = listOf(
            log(1L, "2026-10-05", DayStatus.DONE),
            log(1L, "2026-10-07", DayStatus.DONE),
            log(1L, "2026-10-09", DayStatus.MISSED),
        )
        assertEquals(0, calc.overallStreak(listOf(h), logs, LocalDate.parse("2026-10-11")))
    }

    @Test
    fun silentNegative_showsFullCount() {
        // §7.9: never answered — pending scheduled days count provisionally.
        val h = habit(type = HabitType.NEGATIVE, activeDaysMask = 21, createdOn = LocalDate.parse("2026-10-05"))
        val result = calc.streaks(h, emptyList(), LocalDate.parse("2026-10-11"))
        assertEquals(3, result.current) // Mon + Wed + Fri
        assertEquals(3, result.longest)
    }

    @Test
    fun negativeDaily_noLogs_countsEveryPendingDay() {
        val h = habit(type = HabitType.NEGATIVE, createdOn = LocalDate.parse("2026-10-09"))
        assertEquals(3, calc.currentStreak(h, emptyList(), LocalDate.parse("2026-10-11")))
    }

    @Test
    fun doubleFrozen_addsNothingStaysAlive() {
        val h = habit()
        val logs = listOf(
            log(1L, "2026-10-01", DayStatus.DONE),
            log(1L, "2026-10-02", DayStatus.DONE),
            log(1L, "2026-10-03", DayStatus.FROZEN),
            log(1L, "2026-10-04", DayStatus.FROZEN),
            log(1L, "2026-10-05", DayStatus.DONE),
        )
        val result = calc.streaks(h, logs, LocalDate.parse("2026-10-05"))
        assertEquals(3, result.current)
        assertEquals(3, result.longest)
    }

    @Test
    fun emptyHistory_isZero() {
        // Positive pending days are neutral: neither count nor break.
        val h = habit()
        val result = calc.streaks(h, emptyList(), LocalDate.parse("2026-10-11"))
        assertEquals(0, result.current)
        assertEquals(0, result.longest)
    }

    @Test
    fun pausedAndInactiveGaps_areSkipped() {
        val h = habit()
        val logs = listOf(
            log(1L, "2026-10-01", DayStatus.DONE),
            log(1L, "2026-10-02", DayStatus.PAUSED),
            log(1L, "2026-10-03", DayStatus.NOT_ACTIVE),
            log(1L, "2026-10-04", DayStatus.DONE),
            log(1L, "2026-10-05", DayStatus.DONE),
        )
        val result = calc.streaks(h, logs, LocalDate.parse("2026-10-05"))
        assertEquals(3, result.current)
        assertEquals(3, result.longest)
    }

    @Test
    fun overallIgnoresNegativeHabits() {
        // Negatives never schedule or complete the overall day.
        val pos = habit(id = 1L, activeDaysMask = 21, createdOn = LocalDate.parse("2026-10-05"))
        val neg = habit(id = 2L, type = HabitType.NEGATIVE, createdOn = LocalDate.parse("2026-10-05"))
        val logs = listOf(log(2L, "2026-10-09", DayStatus.CLEAN))
        assertEquals(0, calc.overallStreak(listOf(pos, neg), logs, LocalDate.parse("2026-10-11")))
    }
}
