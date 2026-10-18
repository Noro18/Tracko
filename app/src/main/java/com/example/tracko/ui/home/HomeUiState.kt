package com.example.tracko.ui.home

import com.example.tracko.domain.HeatCell
import com.example.tracko.domain.model.HabitType

/** Everything one habit card shows (FR-20, FR-21, FR-22). */
data class HabitRowUi(
    val id: Long,
    val name: String,
    val type: HabitType,
    val currentStreak: Int,
    val freezeCount: Int,
    val heatmap: List<List<HeatCell>>,
    /** Legal user tap today? Decided by the ViewModel (Task 2/3) — the card only greys. */
    val checkEnabled: Boolean,
)

/** What the Home screen shows. */
data class HomeUiState(
    val habitCount: Int = 0,
    val overallStreak: Int = 0,
    val rows: List<HabitRowUi> = emptyList(),
)
