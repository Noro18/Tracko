package com.example.tracko.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.tracko.data.local.entities.AppStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Raw reads/writes on the single-row `app_state` table. Only
 * [com.example.tracko.data.repository.AppStateRepository] calls these —
 * screens and ViewModels never touch a DAO (layer contract).
 */
@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state WHERE id = 1")
    fun observeState(): Flow<AppStateEntity?>

    @Query("SELECT * FROM app_state WHERE id = 1")
    suspend fun getState(): AppStateEntity?

    @Upsert
    suspend fun upsert(state: AppStateEntity)
}
