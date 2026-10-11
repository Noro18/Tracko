package com.example.tracko.data.repository

import com.example.tracko.data.local.dao.AppStateDao
import com.example.tracko.data.local.dao.HabitDao
import com.example.tracko.data.local.dao.HabitLogDao
import com.example.tracko.data.local.entities.AppStateEntity
import com.example.tracko.data.local.entities.HabitEntity
import com.example.tracko.data.local.entities.HabitLogEntity
import com.example.tracko.domain.LogIntent
import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import com.example.tracko.domain.model.LogEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 5 RED: repository completions, proven with in-memory fake DAOs on plain
 * JVM (the DAOs are interfaces, so no device needed). Written BEFORE the repo
 * bodies exist — failing runs are the proof (TDD Red).
 */
class RepositoryGuardTest {
    private val today = LocalDate.parse("2026-10-11") // a Sunday

    // --- In-memory fakes (mirror decided semantics: upsert replaces) ---

    private class FakeHabitDao : HabitDao {
        val rows = mutableMapOf<Long, HabitEntity>()
        var nextId = 1L
        override fun observeHabits(): Flow<List<HabitEntity>> = flowOf(rows.values.toList())
        override suspend fun getHabit(id: Long): HabitEntity? = rows[id]
        override suspend fun insert(habit: HabitEntity): Long {
            val id = nextId++
            rows[id] = habit.copy(id = id)
            return id
        }
        override suspend fun update(habit: HabitEntity) {
            rows[habit.id] = habit
        }
        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }
    }

    private class FakeHabitLogDao : HabitLogDao {
        val rows = mutableListOf<HabitLogEntity>()
        override fun observeLogs(habitId: Long): Flow<List<HabitLogEntity>> =
            flowOf(rows.filter { it.habitId == habitId })
        override suspend fun logsInRange(from: LocalDate, to: LocalDate): List<HabitLogEntity> =
            rows.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        override suspend fun upsert(log: HabitLogEntity) {
            rows.removeAll { it.habitId == log.habitId && it.date == log.date }
            rows.add(log)
        }
    }

    private class FakeAppStateDao : AppStateDao {
        var state: AppStateEntity? = null
        override fun observeState(): Flow<AppStateEntity?> = flowOf(state)
        override suspend fun getState(): AppStateEntity? = state
        override suspend fun upsert(state: AppStateEntity) {
            this.state = state
        }
    }

    private fun habit(
        activeDaysMask: Int = 127,
        type: HabitType = HabitType.POSITIVE,
        freezeCount: Int = 0,
        freezeThresholdPct: Int = 80,
        createdOn: LocalDate = LocalDate.parse("2026-10-01"),
        pausedSince: LocalDate? = null,
        lastMilestone: Int = 0,
    ) = Habit(
        name = "Run",
        type = type,
        reminderMinutes = 1290,
        activeDaysMask = activeDaysMask,
        freezeCount = freezeCount,
        freezeThresholdPct = freezeThresholdPct,
        createdOn = createdOn,
        pausedSince = pausedSince,
        lastMilestone = lastMilestone,
    )

    // --- Guards (regression pins for data-core task 6) ---

    @Test(expected = IllegalArgumentException::class)
    fun insertRejectsBadMask() {
        runBlocking {
            HabitRepository(FakeHabitDao(), FakeHabitLogDao(), FakeAppStateDao())
                .insert(habit().copy(activeDaysMask = 0))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun insertRejectsBadThreshold() {
        runBlocking {
            HabitRepository(FakeHabitDao(), FakeHabitLogDao(), FakeAppStateDao())
                .insert(habit().copy(freezeThresholdPct = 20))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun insertRejectsBadMilestone() {
        runBlocking {
            HabitRepository(FakeHabitDao(), FakeHabitLogDao(), FakeAppStateDao())
                .insert(habit().copy(lastMilestone = 25))
        }
    }

    // --- Resume backfill (§4.8; resolves the data-core TODO) ---

    @Test
    fun resumeBackfillsPausedWithoutOverwritingDone() = runBlocking {
        val habitDao = FakeHabitDao()
        val logDao = FakeHabitLogDao()
        val repo = HabitRepository(habitDao, logDao, FakeAppStateDao())
        val id = repo.insert(habit(pausedSince = LocalDate.parse("2026-10-08")))
        logDao.upsert(
            HabitLogEntity(habitId = id, date = LocalDate.parse("2026-10-08"), status = DayStatus.DONE, loggedAt = 1L),
        )

        repo.resume(id, today)

        val days = logDao.logsInRange(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-10"))
            .filter { it.habitId == id }.associateBy { it.date }
        // Oct 8 DONE kept; Oct 9 + Oct 10 (yesterday) backfilled PAUSED.
        assertEquals(DayStatus.DONE, days[LocalDate.parse("2026-10-08")]?.status)
        assertEquals(DayStatus.PAUSED, days[LocalDate.parse("2026-10-09")]?.status)
        assertEquals(DayStatus.PAUSED, days[LocalDate.parse("2026-10-10")]?.status)
        assertNull(repo.getHabit(id)?.pausedSince)
    }

    // --- editActiveDays (§4.12; resolves the data-core TODO) ---

    @Test
    fun editActiveDays_finalizesFirstThenDatesTheEdit() = runBlocking {
        val habitDao = FakeHabitDao()
        val logDao = FakeHabitLogDao()
        val repo = HabitRepository(habitDao, logDao, FakeAppStateDao())
        // Mon/Wed/Fri habit, nothing finalized yet.
        val id = repo.insert(habit(activeDaysMask = 21, createdOn = LocalDate.parse("2026-10-05")))

        repo.editActiveDays(id, 127, LocalDate.parse("2026-10-07")) // Wednesday

        val updated = repo.getHabit(id)!!
        // Old rule remembered + edit dated today …
        assertEquals(21, updated.prevActiveDaysMask)
        assertEquals(LocalDate.parse("2026-10-07"), updated.activeDaysEffectiveOn)
        assertEquals(127, updated.activeDaysMask)
        // … and Monday Oct 5 was finalized FIRST (MISSED, no freezes).
        // Tuesday Oct 6 is yesterday relative to the edit → still pending (FR-19).
        val oct5 = logDao.logsInRange(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-05"))
            .first { it.habitId == id }
        assertEquals(DayStatus.MISSED, oct5.status)
        assertTrue(
            logDao.logsInRange(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-06"))
                .none { it.habitId == id },
        )
    }

    // --- upsert validates via LoggingRules (§4.13, FR-31) ---

    @Test(expected = IllegalArgumentException::class)
    fun upsertRejectsTooOldDate() = runBlocking {
        val habitDao = FakeHabitDao()
        val repo = HabitRepository(habitDao, FakeHabitLogDao(), FakeAppStateDao())
        val logRepo = HabitLogRepository(FakeHabitLogDao())
        val id = repo.insert(habit())
        val h = repo.getHabit(id)!!
        logRepo.upsert(
            LogEntry(habitId = id, date = LocalDate.parse("2026-10-09"), status = DayStatus.DONE, loggedAt = 1L),
            h, today,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun upsertRejectsDoneWhereBonusRequired() = runBlocking {
        val habitDao = FakeHabitDao()
        val repo = HabitRepository(habitDao, FakeHabitLogDao(), FakeAppStateDao())
        val logRepo = HabitLogRepository(FakeHabitLogDao())
        // Monday-only habit, today is Sunday → DONE must be BONUS (FR-31).
        val id = repo.insert(habit(activeDaysMask = 1))
        val h = repo.getHabit(id)!!
        logRepo.upsert(
            LogEntry(habitId = id, date = today, status = DayStatus.DONE, loggedAt = 1L),
            h, today,
        )
    }

    @Test
    fun upsertAcceptsBonusOnOffScheduleDay() = runBlocking {
        val logDao = FakeHabitLogDao()
        val repo = HabitRepository(FakeHabitDao(), logDao, FakeAppStateDao())
        val logRepo = HabitLogRepository(logDao)
        val id = repo.insert(habit(activeDaysMask = 1)) // Monday only, today Sunday
        val h = repo.getHabit(id)!!
        logRepo.upsert(
            LogEntry(habitId = id, date = today, status = DayStatus.BONUS, loggedAt = 1L),
            h, today,
        )
        assertTrue(
            logDao.logsInRange(today, today).any { it.status == DayStatus.BONUS },
        )
    }

    @Test
    fun intentMapping_documentsStatusToIntent() {
        // BONUS is a DONE-tap verdict; every other user status maps to itself.
        assertEquals(LogIntent.DONE, LogIntent.forUserStatus(DayStatus.BONUS))
        assertEquals(LogIntent.CLEAN, LogIntent.forUserStatus(DayStatus.CLEAN))
    }

    @Test
    fun finalizeAwardsFreezeForStrongWeek() {
        // End-to-end: 6 DONE + Sunday finalizes → week judged → freeze 0→1.
        runBlocking {
            val habitDao = FakeHabitDao()
            val logDao = FakeHabitLogDao()
            val appStateDao = FakeAppStateDao()
            val repo = HabitRepository(habitDao, logDao, appStateDao)
            val id = repo.insert(habit(createdOn = LocalDate.parse("2026-10-05")))
            for (day in 5..10) {
                logDao.upsert(
                    HabitLogEntity(
                        habitId = id,
                        date = LocalDate.parse("2026-10-%02d".format(day)),
                        status = DayStatus.DONE,
                        loggedAt = 1L,
                    ),
                )
            }
            repo.finalizeHabit(repo.getHabit(id)!!, LocalDate.parse("2026-10-13"))

            val updated = repo.getHabit(id)!!
            assertEquals(1, updated.freezeCount)
            assertEquals(LocalDate.parse("2026-10-05"), updated.lastJudgedWeekStart)
        }
    }
}
