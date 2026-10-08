package com.example.tracko.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.example.tracko.data.local.entities.HabitEntity
import com.example.tracko.data.local.entities.HabitLogEntity

/**
 * One habit with all its logs attached — a read-time join, not a table.
 * No `@Entity` here on purpose: this stores nothing, it only shapes what a
 * query returns for the detail/calendar screens. Derived data is computed,
 * never stored.
 */
data class HabitWithLogs(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habit_id")
    val logs: List<HabitLogEntity>,
)
