package com.example.tracko.domain.model

import java.time.LocalDate

/**
 * One row of habit_log — what happened to one habit on one day (data model §2.2).
 * UNIQUE(habit_id, date): one row per habit per day. No row = pending.
 */
data class LogEntry(
    val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val status: DayStatus,
    /** When you tapped, epoch ms. Null = the system wrote the row (§4.7 relies on this). */
    val loggedAt: Long? = null,
)
