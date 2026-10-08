package com.example.tracko.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tracko.domain.model.DayStatus
import java.time.LocalDate

/**
 * What happened to one habit on one day — the `habit_log` table (data model §2.2).
 * FR-4: deleting a habit wipes its history (CASCADE). One row per habit per
 * day (UNIQUE index); a missing row means pending, never missed.
 */
@Entity(
    tableName = "habit_log",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habit_id", "date"], unique = true)],
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    /** Stored as TEXT "YYYY-MM-DD" via [Converter]. */
    val date: LocalDate,
    /** Stored as TEXT via [Converter]; an unknown string throws in `valueOf()`. */
    val status: DayStatus,
    /** Epoch millis of the user's answer; null when written by the system. */
    @ColumnInfo(name = "logged_at") val loggedAt: Long?,
)
