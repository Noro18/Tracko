package com.example.tracko.domain.model

import java.time.LocalDate

/**
 * The `habit` table as the app thinks about it — one field per column (data model §2.1).
 * Immutable: edits create a new copy. Room's HabitEntity maps to this; domain and UI
 * code should only ever see this type.
 */
data class Habit(
    val id: Long = 0,
    val name: String,
    val type: HabitType,
    val reminderMinutes: Int,
    /** 7-bit mask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64 (FR-1). */
    val activeDaysMask: Int,
    /** The mask in force before the latest edit (§4.12); null until first edit. */
    val prevActiveDaysMask: Int? = null,
    /** The date the latest edit took effect (§4.12); null until first edit. */
    val activeDaysEffectiveOn: LocalDate? = null,
    /** 0..3, own pool per habit (FR-11..FR-13). */
    val freezeCount: Int = 0,
    /** 50..100 percent, default 80 (FR-28). */
    val freezeThresholdPct: Int = 80,
    /** Where this habit's history starts (FR-30). */
    val createdOn: LocalDate,
    /** Non-null means paused; the value is the day the pause began (§4.8). */
    val pausedSince: LocalDate? = null,
    /** Newest Monday already judged for freezes (§4.5); prevents double awards. */
    val lastJudgedWeekStart: LocalDate? = null,
    /** 0, 50, 100 or 150 — which celebration was already shown (FR-25). */
    val lastMilestone: Int = 0,
)
