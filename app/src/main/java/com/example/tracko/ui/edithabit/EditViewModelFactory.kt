package com.example.tracko.ui.edithabit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository

/** Recipe card for [EditViewModel], including create (null) vs edit mode. Built in `MainActivity`. */
class EditViewModelFactory(
    private val habitId: Long?,
    private val habitRepository: HabitRepository,
    private val habitLogRepository: HabitLogRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditViewModel(habitId, habitRepository, habitLogRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel $modelClass")
    }
}
