package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import com.example.tracko.domain.model.LogEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 4 RED: the §4.7 contract as 12 tests. Written BEFORE `WeeklyFreezeJudge`
 * exists, so this file must NOT compile yet — that failure IS the proof (TDD Red).
 * Week under judgment: Mon 2026-10-05 → Sun 2026-10-11.
 */
class WeeklyFreezeJudgeTest {
    private val judge = WeeklyFreezeJudge()
    private val monday = LocalDate.parse("2026-10-05")

    private fun habit(
        type: HabitType = HabitType.POSITIVE,
        freezeCount: Int = 1,
        freezeThresholdPct: Int = 80,
        createdOn: LocalDate = LocalDate.parse("2026-10-01"),
        lastJudgedWeekStart: LocalDate? = null,
    ) = Habit(
        name = "Run",
        type = type,
        reminderMinutes = 1290,
        activeDaysMask = 127,
        freezeCount = freezeCount,
        freezeThresholdPct = freezeThresholdPct,
        createdOn = createdOn,
        lastJudgedWeekStart = lastJudgedWeekStart,
    )

    /** One log per day Mon..Sun from 7 statuses; DONE rows carry loggedAt. */
    private fun week(vararg statuses: DayStatus): List<LogEntry> {
        require(statuses.size == 7) { "A week is 7 days" }
        return statuses.mapIndexed { i, status ->
            LogEntry(
                habitId = 1L,
                date = monday.plusDays(i.toLong()),
                status = status,
                loggedAt = if (status == DayStatus.DONE) 1L else null,
            )
        }
    }

    @Test
    fun fourOfFiveAt80_earnsFreeze() {
        // §7.7a: 4 answered of 5 countable days @80%.
        val h = habit()
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE, DayStatus.DONE,
            DayStatus.MISSED, DayStatus.NOT_ACTIVE, DayStatus.NOT_ACTIVE,
        )
        val verdict = judge.judgeWeek(h, logs, monday)
        assertTrue(verdict.freezeAwarded)
        assertEquals(monday, verdict.lastJudgedWeekStart)
    }

    @Test
    fun threeOfFiveAt80_earnsNothing() {
        val h = habit()
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE, DayStatus.MISSED,
            DayStatus.MISSED, DayStatus.NOT_ACTIVE, DayStatus.NOT_ACTIVE,
        )
        val verdict = judge.judgeWeek(h, logs, monday)
        assertFalse(verdict.freezeAwarded)
        assertEquals(monday, verdict.lastJudgedWeekStart)
    }

    @Test
    fun exactBoundary_earnsFreeze() {
        // Edge: numerator*100 == threshold*denominator counts as met (>=).
        val h = habit(freezeThresholdPct = 60)
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE, DayStatus.MISSED,
            DayStatus.MISSED, DayStatus.NOT_ACTIVE, DayStatus.NOT_ACTIVE,
        )
        assertTrue(judge.judgeWeek(h, logs, monday).freezeAwarded)
    }

    @Test
    fun capAt3_staysAt3() {
        // §7.7b: a perfect week at full pool awards nothing more.
        val h = habit(freezeCount = 3)
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE, DayStatus.DONE,
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE,
        )
        val verdict = judge.judgeWeek(h, logs, monday)
        assertFalse(verdict.freezeAwarded)
        assertEquals(monday, verdict.lastJudgedWeekStart)
    }

    @Test
    fun silentNegatives_earnNothing() {
        // §7.9: 5 silent CLEANs (loggedAt null) → numerator 0.
        val h = habit(type = HabitType.NEGATIVE)
        val logs = (0..6).map {
            LogEntry(habitId = 1L, date = monday.plusDays(it.toLong()), status = DayStatus.CLEAN, loggedAt = null)
        }
        assertFalse(judge.judgeWeek(h, logs, monday).freezeAwarded)
    }

    @Test
    fun answeredNegatives_earn() {
        // Negatives CAN earn — by actually answering (loggedAt set).
        // 4 answered CLEAN + 1 slip of 5 countable days @80%.
        val h = habit(type = HabitType.NEGATIVE)
        val logs = (0..6).map {
            when {
                it < 4 -> LogEntry(habitId = 1L, date = monday.plusDays(it.toLong()), status = DayStatus.CLEAN, loggedAt = 1L)
                it == 4 -> LogEntry(habitId = 1L, date = monday.plusDays(it.toLong()), status = DayStatus.SLIPPED, loggedAt = 2L)
                else -> LogEntry(habitId = 1L, date = monday.plusDays(it.toLong()), status = DayStatus.NOT_ACTIVE, loggedAt = null)
            }
        }
        assertTrue(judge.judgeWeek(h, logs, monday).freezeAwarded)
    }

    @Test
    fun partialWeek_createdMidWeek_skipped() {
        // FR-30: created Wednesday → week is partial, skipped but stamped.
        val h = habit(createdOn = LocalDate.parse("2026-10-07"))
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE, DayStatus.DONE,
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE,
        )
        val verdict = judge.judgeWeek(h, logs, monday)
        assertFalse(verdict.freezeAwarded)
        assertEquals(monday, verdict.lastJudgedWeekStart)
    }

    @Test
    fun pausedWeek_skipped() {
        // §7.4: any PAUSED day in the week → the week is not judged.
        val h = habit()
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.PAUSED, DayStatus.DONE,
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE,
        )
        val verdict = judge.judgeWeek(h, logs, monday)
        assertFalse(verdict.freezeAwarded)
        assertEquals(monday, verdict.lastJudgedWeekStart)
    }

    @Test
    fun denominatorZero_skippedNoError() {
        // §7.12: no countable days → skip quietly, no division, still stamped.
        val h = habit()
        val logs = week(
            DayStatus.NOT_ACTIVE, DayStatus.NOT_ACTIVE, DayStatus.BONUS, DayStatus.NOT_ACTIVE,
            DayStatus.NOT_ACTIVE, DayStatus.NOT_ACTIVE, DayStatus.NOT_ACTIVE,
        )
        val verdict = judge.judgeWeek(h, logs, monday)
        assertFalse(verdict.freezeAwarded)
        assertEquals(monday, verdict.lastJudgedWeekStart)
    }

    @Test
    fun alreadyJudgedWeek_notJudgedTwice() {
        // §4.5: the stamp prevents double awards.
        val h = habit(lastJudgedWeekStart = monday)
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE, DayStatus.DONE,
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE,
        )
        val verdict = judge.judgeWeek(h, logs, monday)
        assertFalse(verdict.freezeAwarded)
        assertEquals(monday, verdict.lastJudgedWeekStart)
    }

    @Test
    fun bonusExcludedFromRate() {
        // §7.10: BONUS is invisible to the rate: 3 DONE of 3 countable = 100%.
        val h = habit()
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE, DayStatus.BONUS,
            DayStatus.BONUS, DayStatus.NOT_ACTIVE, DayStatus.NOT_ACTIVE,
        )
        assertTrue(judge.judgeWeek(h, logs, monday).freezeAwarded)
    }

    @Test
    fun frozenCountsAsNotCompleted() {
        // FROZEN days are countable but never completed: 3/5 = 60% @80.
        val h = habit()
        val logs = week(
            DayStatus.DONE, DayStatus.DONE, DayStatus.DONE, DayStatus.FROZEN,
            DayStatus.FROZEN, DayStatus.NOT_ACTIVE, DayStatus.NOT_ACTIVE,
        )
        assertFalse(judge.judgeWeek(h, logs, monday).freezeAwarded)
    }
}
