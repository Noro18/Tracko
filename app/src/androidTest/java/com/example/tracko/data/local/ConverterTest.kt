package com.example.tracko.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.HabitType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Converter round-trips (SPEC-data-core Task 8, groups 3–4).
 * Dates are `LocalDate` in code but plain `YYYY-MM-DD` TEXT in SQLite (§4.10);
 * enums are types in code but TEXT in SQLite — and an unknown string throws in
 * `valueOf()`, which *is* our status/type enforcement (Room has no `@Check`).
 */
@RunWith(AndroidJUnit4::class)
class ConverterTest {
    private val converter = Converter()

    @Test
    fun localDate_roundTrips() {
        val date = LocalDate.parse("2026-10-07")
        assertEquals("2026-10-07", converter.fromLocalDate(date))
        assertEquals(date, converter.toLocalDate("2026-10-07"))
        assertNull(converter.fromLocalDate(null))
        assertNull(converter.toLocalDate(null))
    }

    @Test
    fun allDayStatusValues_roundTrip() {
        for (status in DayStatus.entries) {
            assertEquals(status.name, converter.fromDayStatus(status))
            assertEquals(status, converter.toDayStatus(status.name))
        }
        assertEquals(8, DayStatus.entries.size)
    }

    @Test
    fun habitType_roundTrips() {
        assertEquals("POSITIVE", converter.fromHabitType(HabitType.POSITIVE))
        assertEquals(HabitType.NEGATIVE, converter.toHabitType("NEGATIVE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownStatusString_throws() {
        converter.toDayStatus("NOPE")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownTypeString_throws() {
        converter.toHabitType("NOPE")
    }
}
