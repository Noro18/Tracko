package com.example.tracko.ui.habitdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository

/** Recipe card for [DetailViewModel], including which habit it shows. Built in `MainActivity`. */
class DetailViewModelFactory(
    private val habitId: Long,
    private val habitRepository: HabitRepository,
    private val habitLogRepository: HabitLogRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(habitId, habitRepository, habitLogRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel $modelClass")
    }
}
