package com.example.tracko.ui.home

import androidx.lifecycle.ViewModel
import com.example.tracko.data.repository.AppStateRepository
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Home shell: holds an empty [HomeUiState]. Constructor already takes the
 * repositories the `home` module will observe — no rewiring later.
 */
class HomeViewModel(
    habitRepository: HabitRepository,
    habitLogRepository: HabitLogRepository,
    appStateRepository: AppStateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
