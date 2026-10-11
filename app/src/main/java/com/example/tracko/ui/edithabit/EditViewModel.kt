package com.example.tracko.ui.edithabit

import androidx.lifecycle.ViewModel
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Edit shell: holds an empty [EditUiState]. Null id = create mode. */
class EditViewModel(
    private val habitId: Long?,
    habitRepository: HabitRepository,
    habitLogRepository: HabitLogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditUiState(habitId = habitId))
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()
}
