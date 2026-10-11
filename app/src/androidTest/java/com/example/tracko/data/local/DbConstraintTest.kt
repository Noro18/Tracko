package com.example.tracko.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tracko.data.local.entities.AppStateEntity
import com.example.tracko.data.local.entities.HabitEntity
import com.example.tracko.data.local.entities.HabitLogEntity
import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.HabitType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DB-enforced rules from data model §2 (SPEC-data-core Task 8).
 * These must run on a device (`connectedDebugAndroidTest`) — plain JVM tests
 * cannot enforce SQLite `UNIQUE` / `CASCADE`. The DB is in-memory, so every
 * test starts empty and nothing real is touched.
 */
@RunWith(AndroidJUnit4::class)
class DbConstraintTest {
    private lateinit var db: TrackoDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, TrackoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun habit(name: String = "Run") = HabitEntity(
        name = name,
        type = HabitType.POSITIVE,
        reminderMinutes = 1_290,
        activeDaysMask = 127,
        prevActiveDaysMask = null,
        activeDaysEffectiveOn = null,
        freezeCount = 0,
        freezeThresholdPct = 80,
        createdOn = LocalDate.parse("2026-10-01"),
        pausedSince = null,
        lastJudgedWeekStart = null,
        lastMilestone = 0,
    )

    @Test
    fun duplicateLog_sameHabitSameDate_secondWriteWinsOneRow() = runBlocking {
        val habitId = db.habitDao().insert(habit())
        val date = LocalDate.parse("2026-10-09")
        db.habitLogDao().upsert(HabitLogEntity(habitId = habitId, date = date, status = DayStatus.DONE, loggedAt = 1L))

        // Same (habit_id, date) again: @Upsert replaces instead of throwing, so a
        // re-tap / "fix yesterday" (FR-19) never crashes — second answer wins.
        db.habitLogDao().upsert(HabitLogEntity(habitId = habitId, date = date, status = DayStatus.MISSED, loggedAt = null))

        val logs = db.habitLogDao().logsInRange(date, date).filter { it.habitId == habitId }
        assertEquals("One row per habit per day: UNIQUE(habit_id, date) (§2.2)", 1, logs.size)
        assertEquals(DayStatus.MISSED, logs[0].status)
    }

    @Test
    fun deleteHabit_wipesItsLogs_cascade() = runBlocking {
        val habitId = db.habitDao().insert(habit())
        db.habitLogDao().upsert(HabitLogEntity(habitId = habitId, date = LocalDate.parse("2026-10-08"), status = DayStatus.DONE, loggedAt = 1L))
        db.habitLogDao().upsert(HabitLogEntity(habitId = habitId, date = LocalDate.parse("2026-10-09"), status = DayStatus.MISSED, loggedAt = null))

        db.habitDao().deleteById(habitId)

        val remaining = db.habitLogDao().logsInRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        assertEquals("Deleting a habit must wipe its history (FR-4, ON DELETE CASCADE)", 0, remaining.size)
    }

    @Test
    fun appState_secondWriteWithId1_replacesInsteadOfDuplicating() = runBlocking {
        // Single-row table guarded by PK id = 1: every write goes through id 1,
        // so a second write replaces the row — the table never holds two rows.
        db.appStateDao().upsert(AppStateEntity(lastFinalizedDate = LocalDate.parse("2026-10-08")))
        db.appStateDao().upsert(AppStateEntity(lastFinalizedDate = LocalDate.parse("2026-10-09"), overallLastMilestone = 50))

        val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM app_state")
        cursor.moveToFirst()
        val rowCount = cursor.getInt(0)
        cursor.close()

        assertEquals("app_state must hold exactly one row (id = 1)", 1, rowCount)
        assertEquals(LocalDate.parse("2026-10-09"), db.appStateDao().getState()?.lastFinalizedDate)
    }
}
