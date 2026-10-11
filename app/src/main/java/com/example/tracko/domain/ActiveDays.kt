package com.example.tracko.domain

import com.example.tracko.domain.model.Habit
import java.time.LocalDate

/**
 * Which 7-bit mask judges [date] for [habit] (data model §4.12, FR-32).
 * Dates before [Habit.activeDaysEffectiveOn] are judged under the mask that was
 * in force then ([Habit.prevActiveDaysMask]); [date] on/after it uses the new
 * [Habit.activeDaysMask]. One previous version is enough because edits always
 * finalize first, leaving at most yesterday + today pending.
 */
fun Habit.maskInForceOn(date: LocalDate): Int {
    val effectiveOn = activeDaysEffectiveOn
    val prev = prevActiveDaysMask
    return if (effectiveOn != null && prev != null && date.isBefore(effectiveOn)) prev else activeDaysMask
}

/**
 * Was [date]'s weekday scheduled under the rule in force on [date] (FR-1)?
 * Bits: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64 (§2.1).
 * `DayOfWeek` numbers Monday=1..Sunday=7, so bit index is `value - 1`.
 */
fun isActiveOn(habit: Habit, date: LocalDate): Boolean {
    val bit = 1 shl (date.dayOfWeek.value - 1)
    return habit.maskInForceOn(date) and bit != 0
}
