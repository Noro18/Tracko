package com.example.tracko.data.local

import androidx.room.TypeConverter
import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.HabitType
import java.time.LocalDate

/**
 * Teaches Room how to store the domain types SQLite has no column for:
 * `LocalDate` and the enums become TEXT. Dates use ISO "YYYY-MM-DD"
 * (`LocalDate.toString()`), so they sort and compare as plain strings.
 *
 * Unknown status/type strings throw in `valueOf()` — that *is* the status
 * enforcement (Room has no `@Check`). Registered on `TrackoDatabase` in task 4.
 */
class Converter {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromDayStatus(value: DayStatus?): String? = value?.name

    @TypeConverter
    fun toDayStatus(value: String?): DayStatus? = value?.let(DayStatus::valueOf)

    @TypeConverter
    fun fromHabitType(value: HabitType?): String? = value?.name

    @TypeConverter
    fun toHabitType(value: String?): HabitType? = value?.let(HabitType::valueOf)
}
