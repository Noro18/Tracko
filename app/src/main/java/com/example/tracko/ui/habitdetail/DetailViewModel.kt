package com.example.tracko.ui.habitdetail

import androidx.lifecycle.ViewModel
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Detail shell: holds an empty [DetailUiState] for [habitId]. */
class DetailViewModel(
    private val habitId: Long,
    habitRepository: HabitRepository,
    habitLogRepository: HabitLogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState(habitId = habitId))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
}
