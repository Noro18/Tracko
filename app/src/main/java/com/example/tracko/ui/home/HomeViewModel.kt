package com.example.tracko.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tracko.data.repository.AppStateRepository
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository
import com.example.tracko.domain.HeatmapBuilder
import com.example.tracko.domain.StreakCalculator
import com.example.tracko.domain.model.Habit
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Home brain: finalizes once, then watches the habit table + a re-build
 * trigger and derives every [HabitRowUi] (streak, freezes, heatmap) from
 * logs — nothing stored (§3). `today` is injected, never read here (§4.13).
 */
class HomeViewModel(
    private val habitRepository: HabitRepository,
    private val habitLogRepository: HabitLogRepository,
    appStateRepository: AppStateRepository, // unused yet — Task 3 keeps the same constructor
    private val today: LocalDate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Bumped after future writes (Task 3 tap) so rows rebuild from fresh logs. */
    private val dataVersion = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            habitRepository.finalizeAll(today)
            combine(habitRepository.observeHabits(), dataVersion) { habits, _ -> habits }
                .collect { habits -> rebuild(habits) }
        }
    }

    private suspend fun rebuild(habits: List<Habit>) {
        if (habits.isEmpty()) {
            _uiState.value = HomeUiState()
            return
        }
        val from = habits.minOf { it.createdOn }
        val logs = habitLogRepository.logsInRange(from, today)
        val calculator = StreakCalculator()
        val rows = habits.map { habit ->
            val mine = logs.filter { it.habitId == habit.id }
            HabitRowUi(
                id = habit.id,
                name = habit.name,
                type = habit.type,
                currentStreak = calculator.currentStreak(habit, mine, today),
                freezeCount = habit.freezeCount,
                heatmap = HeatmapBuilder().build(mine, today, weeks = 12),
                checkEnabled = false, // Task 3: scheduled today && no user row yet
            )
        }
        _uiState.value = HomeUiState(
            habitCount = habits.size,
            overallStreak = calculator.overallStreak(habits, logs, today),
            rows = rows,
        )
    }
}
