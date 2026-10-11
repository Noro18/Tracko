package com.example.tracko.ui.habitdetail

/** What the Detail screen shows. Empty today — the `habit-detail` module fills it. */
data class DetailUiState(
    val habitId: Long = 0L,
    val name: String = "",
)
