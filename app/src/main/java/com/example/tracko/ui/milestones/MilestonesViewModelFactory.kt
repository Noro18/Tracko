package com.example.tracko.ui.milestones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tracko.data.repository.AppStateRepository
import com.example.tracko.data.repository.HabitRepository

/** Recipe card for [MilestonesViewModel]. Built in `MainActivity`. */
class MilestonesViewModelFactory(
    private val habitRepository: HabitRepository,
    private val appStateRepository: AppStateRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MilestonesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MilestonesViewModel(habitRepository, appStateRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel $modelClass")
    }
}
