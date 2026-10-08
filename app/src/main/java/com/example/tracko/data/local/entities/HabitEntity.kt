package com.example.tracko.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.tracko.domain.model.HabitType
import java.time.LocalDate

/**
 * One habit the user tracks — the `habit` table (data model §2.1).
 * Fields and annotations only: no logic, no mappers (those live with the
 * repository in task 6). Dates/enums are stored via [Converter].
 */
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Stored as TEXT "POSITIVE"/"NEGATIVE" via [Converter]. */
    val type: HabitType,
    @ColumnInfo(name = "reminder_minutes") val reminderMinutes: Int,
    @ColumnInfo(name = "active_days_mask") val activeDaysMask: Int,
    @ColumnInfo(name = "prev_active_days_mask") val prevActiveDaysMask: Int?,
    @ColumnInfo(name = "active_days_effective_on") val activeDaysEffectiveOn: LocalDate?,
    @ColumnInfo(name = "freeze_count") val freezeCount: Int = 0,
    @ColumnInfo(name = "freeze_threshold_pct") val freezeThresholdPct: Int = 80,
    @ColumnInfo(name = "created_on") val createdOn: LocalDate,
    @ColumnInfo(name = "paused_since") val pausedSince: LocalDate?,
    @ColumnInfo(name = "last_judged_week_start") val lastJudgedWeekStart: LocalDate?,
    @ColumnInfo(name = "last_milestone") val lastMilestone: Int = 0,
)
