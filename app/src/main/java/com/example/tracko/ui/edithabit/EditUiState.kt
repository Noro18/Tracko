package com.example.tracko.ui.edithabit

/** What the Edit screen shows. `habitId` null = create mode. Empty today — `habit-edit` fills it. */
data class EditUiState(
    val habitId: Long? = null,
    val name: String = "",
)
