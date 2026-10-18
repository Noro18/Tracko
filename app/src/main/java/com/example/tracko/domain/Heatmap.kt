package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.LogEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One square of the heatmap. `status == null` means no row yet (pending). */
data class HeatCell(val date: LocalDate, val status: DayStatus?, val isFuture: Boolean)

/**
 * Lays out the last [weeks] weeks as columns of 7 days, Monday first, like
 * GitHub (FR-20). Pure Kotlin: `today` is passed in, no clock (NFR-6).
 */
class HeatmapBuilder {
    fun build(logs: List<LogEntry>, today: LocalDate, weeks: Int = 16): List<List<HeatCell>> {
        val byDate = logs.associateBy { it.date }
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = thisMonday.minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { week ->
            (0 until 7).map { day ->
                val date = firstMonday.plusDays((week * 7 + day).toLong())
                HeatCell(date, byDate[date]?.status, date.isAfter(today))
            }
        }
    }
}
