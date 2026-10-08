package com.example.tracko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.tracko.data.local.entities.HabitEntity
import kotlinx.coroutines.flow.Flow

/**
 * Raw reads/writes on the `habit` table. Only [com.example.tracko.data.repository.HabitRepository]
 * calls these — screens and ViewModels never touch a DAO (layer contract).
 */
@Dao
interface HabitDao {
    @Query("SELECT * FROM habit ORDER BY id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    /** Logs go with it via `ON DELETE CASCADE` (FR-4). */
    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun deleteById(id: Long)
}
