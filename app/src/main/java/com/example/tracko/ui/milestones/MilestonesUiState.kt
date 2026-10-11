package com.example.tracko.ui.milestones

/** What the Milestones screen shows: whole-app stats only, never per-habit (decided 2026-10-11). */
data class MilestonesUiState(
    val overallStreak: Int = 0,
    val overallMilestone: Int = 0,
)
