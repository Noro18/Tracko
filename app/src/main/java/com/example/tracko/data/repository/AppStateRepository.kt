package com.example.tracko.data.repository

import com.example.tracko.data.local.dao.AppStateDao
import com.example.tracko.data.local.entities.AppStateEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Owns the single-row `app_state` table (data model §2.3, `id = 1`).
 * Holds the finalizer bookmark `last_finalized_date` (§4.6 catch-up) and the
 * overall milestone (FR-25). No domain model — this is internal bookkeeping,
 * not habit vocabulary.
 */
class AppStateRepository(private val dao: AppStateDao) {
    fun observeState(): Flow<AppStateEntity?> = dao.observeState()

    suspend fun getState(): AppStateEntity? = dao.getState()

    suspend fun updateLastFinalizedDate(date: LocalDate) {
        val current = dao.getState() ?: AppStateEntity()
        dao.upsert(current.copy(lastFinalizedDate = date))
    }

    suspend fun updateOverallMilestone(value: Int) {
        require(value in setOf(0, 50, 100, 150)) { "overall milestone must be 0/50/100/150 (FR-25)" }
        val current = dao.getState() ?: AppStateEntity()
        dao.upsert(current.copy(overallLastMilestone = value))
    }
}
