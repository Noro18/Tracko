package com.example.tracko.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.tracko.data.local.entities.HabitLogEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Raw reads/writes on the `habit_log` table. Only
 * [com.example.tracko.data.repository.HabitLogRepository] calls these —
 * screens and ViewModels never touch a DAO (layer contract).
 */
@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_log WHERE habit_id = :habitId ORDER BY date")
    fun observeLogs(habitId: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_log WHERE date BETWEEN :from AND :to ORDER BY habit_id, date")
    suspend fun logsInRange(from: LocalDate, to: LocalDate): List<HabitLogEntity>

    /**
     * Insert or replace by primary key. Note: this does NOT enforce the
     * `UNIQUE(habit_id, date)` rule's *meaning* — the repository owns the
     * guards (which date may be written, and when).
     */
    @Upsert
    suspend fun upsert(log: HabitLogEntity)
}
