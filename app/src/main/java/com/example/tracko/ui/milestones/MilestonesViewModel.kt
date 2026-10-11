package com.example.tracko.ui.milestones

import androidx.lifecycle.ViewModel
import com.example.tracko.data.repository.AppStateRepository
import com.example.tracko.data.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Milestones shell: holds empty whole-app stats. Celebrations arrive in `milestones`. */
class MilestonesViewModel(
    habitRepository: HabitRepository,
    appStateRepository: AppStateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MilestonesUiState())
    val uiState: StateFlow<MilestonesUiState> = _uiState.asStateFlow()
}
