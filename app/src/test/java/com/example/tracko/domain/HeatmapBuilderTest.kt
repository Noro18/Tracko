package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.LogEntry
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Heatmap grid (FR-20): Monday-first week columns, explicit `today`, no clock. */
class HeatmapBuilderTest {
    private val builder = HeatmapBuilder()
    private val today = LocalDate.parse("2026-10-07") // a Wednesday

    @Test
    fun grid_isWeeksColumnsBySevenRows() {
        val grid = builder.build(emptyList(), today, weeks = 12)
        assertEquals(12, grid.size)
        assertTrue(grid.all { it.size == 7 })
    }

    @Test
    fun everyColumn_startsOnMonday_lastIsThisWeek() {
        val grid = builder.build(emptyList(), today, weeks = 12)
        assertTrue(grid.all { it.first().date.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(LocalDate.parse("2026-10-05"), grid.last().first().date)
    }

    @Test
    fun daysAfterToday_areMarkedFuture() {
        val last = builder.build(emptyList(), today, weeks = 12).last()
        assertEquals(
            listOf(false, false, false, true, true, true, true), // Mon..Sun
            last.map { it.isFuture },
        )
    }

    @Test
    fun status_comesFromLogs_noRowIsNull() {
        val logs = listOf(
            LogEntry(habitId = 1L, date = LocalDate.parse("2026-10-05"), status = DayStatus.DONE, loggedAt = 1L),
            LogEntry(habitId = 1L, date = LocalDate.parse("2026-10-06"), status = DayStatus.FROZEN),
        )
        val last = builder.build(logs, today, weeks = 12).last()
        assertEquals(DayStatus.DONE, last[0].status)
        assertEquals(DayStatus.FROZEN, last[1].status)
        assertNull(last[2].status) // Wednesday: no row = pending
    }
}
