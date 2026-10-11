package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import com.example.tracko.domain.model.LogEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 3 RED: the §4.6 contract as 13 tests. Written BEFORE `Finalizer` exists,
 * so this file must NOT compile yet — that failure IS the proof (TDD Red).
 * Each test builds a habit + existing rows in code, passes an explicit `today`,
 * and asserts the returned [FinalizePlan]. Calendar: 2026-10-11 is a Sunday.
 */
class FinalizerTest {
    private val finalizer = Finalizer()
    private val today = LocalDate.parse("2026-10-11")

    private fun habit(
        type: HabitType = HabitType.POSITIVE,
        activeDaysMask: Int = 127,
        prevActiveDaysMask: Int? = null,
        activeDaysEffectiveOn: LocalDate? = null,
        freezeCount: Int = 0,
        lastMilestone: Int = 0,
        createdOn: LocalDate = LocalDate.parse("2026-10-01"),
        pausedSince: LocalDate? = null,
    ) = Habit(
        name = "Run",
        type = type,
        reminderMinutes = 1290,
        activeDaysMask = activeDaysMask,
        prevActiveDaysMask = prevActiveDaysMask,
        activeDaysEffectiveOn = activeDaysEffectiveOn,
        freezeCount = freezeCount,
        lastMilestone = lastMilestone,
        createdOn = createdOn,
        pausedSince = pausedSince,
    )

    private fun log(date: String, status: DayStatus, loggedAt: Long? = 1L) = LogEntry(
        habitId = 1L,
        date = LocalDate.parse(date),
        status = status,
        loggedAt = loggedAt,
    )

    @Test
    fun existingDoneRow_isKept() {
        val h = habit()
        val plan = finalizer.finalize(
            h, listOf(log("2026-10-08", DayStatus.DONE)),
            LocalDate.parse("2026-10-07"), today,
        )
        assertTrue(
            "Existing rows are never overwritten (§4.6 step 1)",
            plan.logsToWrite.none { it.date == LocalDate.parse("2026-10-08") },
        )
    }

    @Test
    fun slippedWithFreeze_becomesFrozen() {
        val h = habit(type = HabitType.NEGATIVE, freezeCount = 1)
        val plan = finalizer.finalize(
            h, listOf(log("2026-10-08", DayStatus.SLIPPED)),
            LocalDate.parse("2026-10-07"), today,
        )
        val frozen = plan.logsToWrite.filter { it.status == DayStatus.FROZEN }
        assertEquals(1, frozen.size)
        assertEquals(LocalDate.parse("2026-10-08"), frozen[0].date)
        assertEquals(0, plan.freezeCount)
    }

    @Test
    fun slippedWithoutFreeze_staysSlippedResetsMilestone() {
        val h = habit(type = HabitType.NEGATIVE, freezeCount = 0, lastMilestone = 50)
        val plan = finalizer.finalize(
            h, listOf(log("2026-10-08", DayStatus.SLIPPED)),
            LocalDate.parse("2026-10-07"), today,
        )
        assertTrue(plan.logsToWrite.none { it.date == LocalDate.parse("2026-10-08") })
        assertEquals(0, plan.lastMilestone)
    }

    @Test
    fun missedDayNoFreeze_writesMissedResetsMilestone() {
        val h = habit(lastMilestone = 50, createdOn = LocalDate.parse("2026-10-08"))
        val plan = finalizer.finalize(h, emptyList(), null, today)
        val missed = plan.logsToWrite.filter { it.status == DayStatus.MISSED }
        assertEquals(listOf("2026-10-08", "2026-10-09"), missed.map { it.date.toString() })
        assertEquals(0, plan.lastMilestone)
    }

    @Test
    fun missedDayWithFreeze_writesFrozen() {
        val h = habit(freezeCount = 1, createdOn = LocalDate.parse("2026-10-09"))
        val plan = finalizer.finalize(h, emptyList(), LocalDate.parse("2026-10-08"), today)
        assertEquals(1, plan.logsToWrite.size)
        assertEquals(DayStatus.FROZEN, plan.logsToWrite[0].status)
        assertEquals(0, plan.freezeCount)
    }

    @Test
    fun yesterdayAndToday_neverTouched() {
        // FR-19: yesterday stays editable, so the plan must stop at today - 2.
        val h = habit()
        val plan = finalizer.finalize(h, emptyList(), LocalDate.parse("2026-10-09"), today)
        assertTrue(plan.logsToWrite.isEmpty())
        assertEquals(LocalDate.parse("2026-10-09"), plan.lastFinalizedDate)
    }

    @Test
    fun pausedDay_writesPaused() {
        val h = habit(pausedSince = LocalDate.parse("2026-10-08"))
        val plan = finalizer.finalize(h, emptyList(), LocalDate.parse("2026-10-07"), today)
        val paused = plan.logsToWrite.filter { it.status == DayStatus.PAUSED }
        assertEquals(listOf("2026-10-08", "2026-10-09"), paused.map { it.date.toString() })
    }

    @Test
    fun tuesdayStaysNotActiveAfterEdit() {
        // §7.5: Mon/Wed/Fri → daily on Wed Oct 7; Tuesday keeps the old rule.
        val h = habit(
            activeDaysMask = 127,
            prevActiveDaysMask = 21,
            activeDaysEffectiveOn = LocalDate.parse("2026-10-07"),
        )
        val plan = finalizer.finalize(h, emptyList(), LocalDate.parse("2026-10-05"), today)
        val oct6 = plan.logsToWrite.filter { it.date == LocalDate.parse("2026-10-06") }
        assertEquals(1, oct6.size)
        assertEquals(DayStatus.NOT_ACTIVE, oct6[0].status)
    }

    @Test
    fun phoneOff4Days_catchesUpInOrder() {
        // §7.6: the bookmark makes the routine resume where it stopped.
        val h = habit()
        val plan = finalizer.finalize(h, emptyList(), LocalDate.parse("2026-10-05"), today)
        assertEquals(
            listOf("2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09"),
            plan.logsToWrite.map { it.date.toString() },
        )
        assertEquals(LocalDate.parse("2026-10-09"), plan.lastFinalizedDate)
    }

    @Test
    fun existingBonusRow_neverCrashes() {
        // §7.10: an off-schedule BONUS row must survive finalization untouched.
        val h = habit(activeDaysMask = 1) // Monday only
        val plan = finalizer.finalize(
            h, listOf(log("2026-10-04", DayStatus.BONUS)), // Sunday, off-schedule
            LocalDate.parse("2026-10-03"), today,
        )
        assertTrue(
            "BONUS rows are existing rows: keep them (§4.6 step 1)",
            plan.logsToWrite.none { it.date == LocalDate.parse("2026-10-04") },
        )
    }

    @Test
    fun pauseOverDone_keepsDone() {
        // §7.11: pausing over an answered day never overwrites it.
        val h = habit(pausedSince = LocalDate.parse("2026-10-08"))
        val plan = finalizer.finalize(
            h, listOf(log("2026-10-08", DayStatus.DONE)),
            LocalDate.parse("2026-10-07"), today,
        )
        assertTrue(plan.logsToWrite.none { it.date == LocalDate.parse("2026-10-08") })
        assertEquals(listOf("2026-10-09"), plan.logsToWrite.map { it.date.toString() })
        assertEquals(DayStatus.PAUSED, plan.logsToWrite[0].status)
    }

    @Test
    fun negativeMissingDay_writesSilentClean() {
        val h = habit(type = HabitType.NEGATIVE, createdOn = LocalDate.parse("2026-10-09"))
        val plan = finalizer.finalize(h, emptyList(), LocalDate.parse("2026-10-08"), today)
        assertEquals(1, plan.logsToWrite.size)
        assertEquals(DayStatus.CLEAN, plan.logsToWrite[0].status)
        assertNull("System-written rows carry no tap timestamp (§4.7 relies on this)", plan.logsToWrite[0].loggedAt)
    }

    @Test
    fun neverFinalizesBeforeCreatedOn() {
        val h = habit(createdOn = LocalDate.parse("2026-10-08"))
        val plan = finalizer.finalize(h, emptyList(), LocalDate.parse("2026-10-01"), today)
        assertTrue(plan.logsToWrite.all { !it.date.isBefore(LocalDate.parse("2026-10-08")) })
        assertTrue(!plan.lastFinalizedDate.isBefore(LocalDate.parse("2026-10-08")))
    }
}
