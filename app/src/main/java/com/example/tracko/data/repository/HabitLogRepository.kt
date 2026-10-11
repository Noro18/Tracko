package com.example.tracko.data.repository

import com.example.tracko.data.local.dao.HabitLogDao
import com.example.tracko.data.local.entities.HabitLogEntity
import com.example.tracko.domain.LogIntent
import com.example.tracko.domain.LoggingRules
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.LogEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only way to write `habit_log` rows (architecture layer contract).
 * UI never constructs a [HabitLogEntity] directly — single write path:
 * screen event → ViewModel → here.
 *
 * Missing row = pending, never missed (§2.2). Full logging guards
 * (today/yesterday only, never before `created_on`, `BONUS` on off-schedule
 * days per FR-31 + §4.13) live in `LoggingRules` in `streak-engine` (slice 2)
 * and are deliberately NOT duplicated here.
 */
class HabitLogRepository(private val dao: HabitLogDao) {
    fun observeLogs(habitId: Long): Flow<List<LogEntry>> =
        dao.observeLogs(habitId).map { list -> list.map { it.toDomain() } }

    suspend fun logsInRange(from: LocalDate, to: LocalDate): List<LogEntry> =
        dao.logsInRange(from, to).map { it.toDomain() }

    /**
     * The single user write path for check-ins (FR-5, FR-6, FR-31): validates
     * through `LoggingRules` before writing (§4.13) — window, `createdOn`, and
     * `BONUS`-vs-`DONE` correctness. System rows (`MISSED`, `FROZEN`,
     * `NOT_ACTIVE`, `PAUSED`) are written by the `Finalizer` via
     * `HabitRepository`, never here.
     */
    suspend fun upsert(entry: LogEntry, habit: Habit, today: LocalDate) {
        val expected = LoggingRules().validate(habit, entry.date, LogIntent.forUserStatus(entry.status), today)
        require(expected == entry.status) {
            "Status $entry.status does not match logging rules (expected $expected, FR-31 §4.13)"
        }
        dao.upsert(entry.toEntity())
    }

    private fun LogEntry.toEntity() = HabitLogEntity(
        id = id,
        habitId = habitId,
        date = date,
        status = status,
        loggedAt = loggedAt,
    )

    private fun HabitLogEntity.toDomain() = LogEntry(
        id = id,
        habitId = habitId,
        date = date,
        status = status,
        loggedAt = loggedAt,
    )
}
