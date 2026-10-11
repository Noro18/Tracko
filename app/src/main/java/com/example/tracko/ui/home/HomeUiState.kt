package com.example.tracko.ui.home

/** What the Home screen shows. Empty today — the `home` module fills it. */
data class HomeUiState(
    val habitCount: Int = 0,
    val overallStreak: Int = 0,
)
