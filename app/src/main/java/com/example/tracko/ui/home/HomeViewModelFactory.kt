package com.example.tracko.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tracko.data.repository.AppStateRepository
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository

/** Recipe card: lets Android build [HomeViewModel] with its repositories. Built in `MainActivity`. */
class HomeViewModelFactory(
    private val habitRepository: HabitRepository,
    private val habitLogRepository: HabitLogRepository,
    private val appStateRepository: AppStateRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(habitRepository, habitLogRepository, appStateRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel $modelClass")
    }
}
