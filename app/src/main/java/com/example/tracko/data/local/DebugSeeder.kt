package com.example.tracko.data.local

import com.example.tracko.data.local.dao.HabitLogDao
import com.example.tracko.data.local.entities.HabitLogEntity
import com.example.tracko.data.repository.HabitRepository
import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * TEMPORARY demo data, debug builds only (no habit-creation UI exists yet).
 * Seeds two habits + ~9 weeks of history through the repositories, then runs
 * the real finalizer so gaps are inked `MISSED` exactly like genuine history.
 *
 * DELETE when the Edit module ships. Testing rules (§4.13 today/yesterday)
 * and `HabitLogRepository.upsert` apply to user taps — this deliberately
 * writes older days straight through [HabitLogDao]: they simulate days that
 * already happened, which no tap is allowed to produce.
 */
object DebugSeeder {

    suspend fun seedIfEmpty(
        habitRepository: HabitRepository,
        logDao: HabitLogDao,
        today: LocalDate,
    ) {
        if (habitRepository.observeHabits().first().isNotEmpty()) return

        val runId = habitRepository.insert(
            Habit(
                name = "Run",
                type = HabitType.POSITIVE,
                reminderMinutes = 1290,
                activeDaysMask = 127,
                createdOn = today.minusDays(45),
            ),
        )
        val noSugarId = habitRepository.insert(
            Habit(
                name = "No sugar",
                type = HabitType.NEGATIVE,
                reminderMinutes = 1290,
                activeDaysMask = 127,
                createdOn = today.minusDays(30),
            ),
        )

        // "Run": done daily except scattered skips older than 10 days back.
        // Days 1..9 (incl. yesterday) answered, only today pending → streak reads 9.
        for (i in 45 downTo 1) {
            val day = today.minusDays(i.toLong())
            val answered = i <= 9 || !(i % 7 == 2 || i % 5 == 0)
            if (answered) {
                logDao.upsert(HabitLogEntity(habitId = runId, date = day, status = DayStatus.DONE, loggedAt = noon(day)))
            }
        }

        // "No sugar": mostly clean, two slips 3 and 5 weeks back.
        for (i in 30 downTo 1) {
            val day = today.minusDays(i.toLong())
            val status = if (i == 27 || i == 21) DayStatus.SLIPPED else DayStatus.CLEAN
            logDao.upsert(HabitLogEntity(habitId = noSugarId, date = day, status = status, loggedAt = noon(day)))
        }

        habitRepository.finalizeAll(today)
    }

    /** Deterministic fake "tapped at noon" timestamp — stable across reseeds. */
    private fun noon(day: LocalDate): Long = day.toEpochDay() * 86_400_000L + 43_200_000L
}
