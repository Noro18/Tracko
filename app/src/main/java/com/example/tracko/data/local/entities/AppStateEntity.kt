package com.example.tracko.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Global, non-per-habit state — the `app_state` table (data model §2.3).
 * Exactly one row exists (id = 1, never auto-generated).
 */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Long = 1,
    @ColumnInfo(name = "last_finalized_date") val lastFinalizedDate: LocalDate? = null,
    @ColumnInfo(name = "overall_last_milestone") val overallLastMilestone: Int = 0,
)
