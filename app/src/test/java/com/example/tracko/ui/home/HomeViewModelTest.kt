package com.example.tracko.ui.home

import com.example.tracko.data.local.dao.AppStateDao
import com.example.tracko.data.local.dao.HabitDao
import com.example.tracko.data.local.dao.HabitLogDao
import com.example.tracko.data.local.entities.AppStateEntity
import com.example.tracko.data.local.entities.HabitEntity
import com.example.tracko.data.local.entities.HabitLogEntity
import com.example.tracko.data.repository.AppStateRepository
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository
import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.HabitType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 2 RED: the ViewModel contract. Written BEFORE `HomeViewModel` observes
 * anything and BEFORE `HabitRepository.finalizeAll` exists — this file must NOT
 * compile yet. That failure IS the proof (TDD Red).
 */
class HomeViewModelTest {
    private val today = LocalDate.parse("2026-10-11") // a Sunday
    private val dispatcher = StandardTestDispatcher()

    // --- In-memory fakes (same shape as RepositoryGuardTest's) ---

    private class FakeHabitDao(var rows: List<HabitEntity> = emptyList()) : HabitDao {
        override fun observeHabits(): Flow<List<HabitEntity>> = flowOf(rows)
        override suspend fun getHabit(id: Long): HabitEntity? = rows.find { it.id == id }
        override suspend fun insert(habit: HabitEntity): Long = 1L
        override suspend fun update(habit: HabitEntity) {
            rows = rows.map { if (it.id == habit.id) habit else it }
        }
        override suspend fun deleteById(id: Long) {
            rows = rows.filter { it.id != id }
        }
    }

    private class FakeHabitLogDao(val rows: MutableList<HabitLogEntity> = mutableListOf()) : HabitLogDao {
        override fun observeLogs(habitId: Long): Flow<List<HabitLogEntity>> =
            flowOf(rows.filter { it.habitId == habitId })
        override suspend fun logsInRange(from: LocalDate, to: LocalDate): List<HabitLogEntity> =
            rows.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        override suspend fun upsert(log: HabitLogEntity) {
            rows.removeAll { it.habitId == log.habitId && it.date == log.date }
            rows.add(log)
        }
    }

    private class FakeAppStateDao(var state: AppStateEntity? = null) : AppStateDao {
        override fun observeState(): Flow<AppStateEntity?> = flowOf(state)
        override suspend fun getState(): AppStateEntity? = state
        override suspend fun upsert(state: AppStateEntity) {
            this.state = state
        }
    }

    private fun habitEntity(
        id: Long = 1L,
        type: HabitType = HabitType.POSITIVE,
        createdOn: LocalDate = LocalDate.parse("2026-10-01"),
    ) = HabitEntity(
        id = id,
        name = "Run",
        type = type,
        reminderMinutes = 1290,
        activeDaysMask = 127,
        prevActiveDaysMask = null,
        activeDaysEffectiveOn = null,
        createdOn = createdOn,
        pausedSince = null,
        lastJudgedWeekStart = null,
    )

    private fun repos(
        habits: List<HabitEntity> = emptyList(),
        logs: MutableList<HabitLogEntity> = mutableListOf(),
        state: AppStateEntity? = null,
    ): Triple<HabitRepository, HabitLogRepository, AppStateRepository> {
        val habitDao = FakeHabitDao(habits)
        val logDao = FakeHabitLogDao(logs)
        val appStateDao = FakeAppStateDao(state)
        return Triple(
            HabitRepository(habitDao, logDao, appStateDao),
            HabitLogRepository(logDao),
            AppStateRepository(appStateDao),
        )
    }

    private fun viewModel(
        habits: List<HabitEntity> = emptyList(),
        logs: MutableList<HabitLogEntity> = mutableListOf(),
        state: AppStateEntity? = null,
    ): HomeViewModel {
        val (habitRepo, logRepo, appStateRepo) = repos(habits, logs, state)
        return HomeViewModel(habitRepo, logRepo, appStateRepo, today)
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyWithNoHabits() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.first()
        assertTrue(state.rows.isEmpty())
        assertEquals(0, state.overallStreak)
    }

    @Test
    fun buildsRowsFromSeededLogs() = runTest {
        val logs = mutableListOf(
            HabitLogEntity(habitId = 1L, date = LocalDate.parse("2026-10-09"), status = DayStatus.DONE, loggedAt = 1L),
            HabitLogEntity(habitId = 1L, date = LocalDate.parse("2026-10-10"), status = DayStatus.DONE, loggedAt = 2L),
        )
        val vm = viewModel(habits = listOf(habitEntity()), logs = logs)
        advanceUntilIdle()
        val state = vm.uiState.first { it.rows.isNotEmpty() }
        assertEquals(1, state.rows.size)
        assertEquals("Run", state.rows[0].name)
        // Oct 11 (today) pending + Oct 10, 9 done → streak 2.
        assertEquals(2, state.rows[0].currentStreak)
        assertEquals(0, state.rows[0].freezeCount)
        // Heatmap carries the seeded statuses on their dates.
        val cells = state.rows[0].heatmap.flatten().associateBy { it.date }
        assertEquals(DayStatus.DONE, cells[LocalDate.parse("2026-10-09")]?.status)
    }

    @Test
    fun finalizeAllRunsBeforeFirstEmit() = runTest {
        // Stale bookmark Oct 1, nothing logged: Oct 2..9 must be inked MISSED first.
        val appStateDao = FakeAppStateDao(AppStateEntity(lastFinalizedDate = LocalDate.parse("2026-10-01")))
        val logDao = FakeHabitLogDao()
        val habitDao = FakeHabitDao(listOf(habitEntity()))
        val habitRepo = HabitRepository(habitDao, logDao, appStateDao)
        val vm = HomeViewModel(habitRepo, HabitLogRepository(logDao), AppStateRepository(appStateDao), today)
        advanceUntilIdle()

        assertEquals(LocalDate.parse("2026-10-09"), appStateDao.state?.lastFinalizedDate)
        val oct5 = logDao.rows.first { it.date == LocalDate.parse("2026-10-05") }
        assertEquals(DayStatus.MISSED, oct5.status)
        // … and the rows the UI sees already reflect finalized history.
        assertTrue(vm.uiState.first().rows.isNotEmpty())
    }

    @Test
    fun overallStreakAcrossTwoHabits() = runTest {
        val logs = mutableListOf(
            HabitLogEntity(habitId = 1L, date = LocalDate.parse("2026-10-10"), status = DayStatus.DONE, loggedAt = 1L),
            HabitLogEntity(habitId = 2L, date = LocalDate.parse("2026-10-10"), status = DayStatus.DONE, loggedAt = 1L),
        )
        val vm = viewModel(habits = listOf(habitEntity(1L), habitEntity(2L)), logs = logs)
        advanceUntilIdle()
        // Oct 11 pending (skipped), Oct 10 completed → overall 1.
        assertEquals(1, vm.uiState.first { it.rows.size == 2 }.overallStreak)
    }

}
