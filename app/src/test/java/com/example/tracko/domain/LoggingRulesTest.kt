package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 1 proof: the logging window + BONUS (§4.13, FR-31) and the mask in force
 * per date (§4.12). Plain JVM, explicit `today` — no clock, no device (NFR-6).
 */
class LoggingRulesTest {
    private val rules = LoggingRules()
    private val today = LocalDate.parse("2026-10-11") // a Sunday

    private fun habit(
        type: HabitType = HabitType.POSITIVE,
        activeDaysMask: Int = 127, // every day
        prevActiveDaysMask: Int? = null,
        activeDaysEffectiveOn: LocalDate? = null,
        createdOn: LocalDate = LocalDate.parse("2026-10-01"),
    ) = Habit(
        name = "Run",
        type = type,
        reminderMinutes = 1290,
        activeDaysMask = activeDaysMask,
        prevActiveDaysMask = prevActiveDaysMask,
        activeDaysEffectiveOn = activeDaysEffectiveOn,
        createdOn = createdOn,
    )

    @Test
    fun doneToday_onScheduledDay_isDone() {
        assertEquals(
            DayStatus.DONE,
            rules.validate(habit(), today, LogIntent.DONE, today),
        )
    }

    @Test
    fun doneYesterday_onScheduledDay_isDone_fixYesterday() {
        // FR-19: yesterday stays editable.
        assertEquals(
            DayStatus.DONE,
            rules.validate(habit(), today.minusDays(1), LogIntent.DONE, today),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun twoDaysAgo_isRejected() {
        rules.validate(habit(), today.minusDays(2), LogIntent.DONE, today)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tomorrow_isRejected() {
        rules.validate(habit(), today.plusDays(1), LogIntent.DONE, today)
    }

    @Test(expected = IllegalArgumentException::class)
    fun beforeCreatedOn_isRejected() {
        val h = habit(createdOn = today)
        rules.validate(h, today.minusDays(1), LogIntent.DONE, today)
    }

    @Test
    fun doneToday_onNonScheduledDay_isBonus() {
        // FR-31: positive DONE off-schedule earns BONUS, never MISSED.
        val mondayOnly = habit(activeDaysMask = 1) // today is Sunday
        assertFalse(isActiveOn(mondayOnly, today))
        assertEquals(
            DayStatus.BONUS,
            rules.validate(mondayOnly, today, LogIntent.DONE, today),
        )
    }

    @Test
    fun negativeTaps_mapToCleanAndSlipped() {
        val h = habit(type = HabitType.NEGATIVE)
        assertEquals(DayStatus.CLEAN, rules.validate(h, today, LogIntent.CLEAN, today))
        assertEquals(DayStatus.SLIPPED, rules.validate(h, today, LogIntent.SLIPPED, today))
    }

    @Test(expected = IllegalArgumentException::class)
    fun positive_rejectsNoYesTaps() {
        rules.validate(habit(), today, LogIntent.CLEAN, today)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negative_rejectsDoneTap() {
        rules.validate(habit(type = HabitType.NEGATIVE), today, LogIntent.DONE, today)
    }

    @Test
    fun editedMask_oldDateUsesPrevMask_newDateUsesNewMask() {
        // Mon/Wed/Fri (1+4+16=21) → every day, effective Wednesday Oct 7.
        val h = habit(
            activeDaysMask = 127,
            prevActiveDaysMask = 21,
            activeDaysEffectiveOn = LocalDate.parse("2026-10-07"),
        )
        val tuesday = LocalDate.parse("2026-10-06")
        val wednesday = LocalDate.parse("2026-10-07")

        assertFalse(isActiveOn(h, tuesday)) // judged under the old rule
        assertTrue(isActiveOn(h, wednesday)) // new rule starts on effective day
        assertEquals(DayStatus.BONUS, rules.validate(h, tuesday, LogIntent.DONE, wednesday))
        assertEquals(DayStatus.DONE, rules.validate(h, wednesday, LogIntent.DONE, wednesday))
    }
}
