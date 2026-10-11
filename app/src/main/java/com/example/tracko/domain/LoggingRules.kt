package com.example.tracko.domain

import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.Habit
import com.example.tracko.domain.model.HabitType
import java.time.LocalDate

/** What the user meant by a tap: Done on a positive habit, No/Yes on a negative one. */
enum class LogIntent {
    DONE,
    CLEAN,
    SLIPPED,
    ;

    companion object {
        /**
         * Maps a user-written status back to its tap. `BONUS` is a DONE-tap
         * verdict (FR-31). System statuses (`MISSED`, `FROZEN`, `NOT_ACTIVE`,
         * `PAUSED`) are written by the `Finalizer`, never by upsert.
         */
        fun forUserStatus(status: DayStatus): LogIntent = when (status) {
            DayStatus.DONE, DayStatus.BONUS -> DONE
            DayStatus.CLEAN -> CLEAN
            DayStatus.SLIPPED -> SLIPPED
            DayStatus.MISSED, DayStatus.FROZEN, DayStatus.NOT_ACTIVE, DayStatus.PAUSED ->
                throw IllegalArgumentException("System statuses are written by the Finalizer, not upsert (§4.6)")
        }
    }
}

/**
 * The bouncer at the write path (data model §4.13; FR-5, FR-6, FR-7, FR-19, FR-31).
 * Decides whether a tap is allowed and what status it earns. Pure Kotlin: no
 * Android, no database, `today` passed in so tests can pretend any date.
 * The repository calls this; UI never constructs rows (layer contract).
 */
class LoggingRules {
    /**
     * Returns the [DayStatus] to store, or throws `IllegalArgumentException`.
     * Window: [date] must be `today` or yesterday, and never before the habit's
     * `createdOn`. A positive DONE on a non-scheduled day earns [DayStatus.BONUS]
     * (counts in streaks, ignored for freezes); negative habits have no bonus days.
     */
    fun validate(habit: Habit, date: LocalDate, intent: LogIntent, today: LocalDate): DayStatus {
        val yesterday = today.minusDays(1)
        require(date == today || date == yesterday) {
            "Logging is allowed only for today and yesterday (FR-19): date=$date today=$today"
        }
        require(!date.isBefore(habit.createdOn)) {
            "Logging is never allowed before createdOn (FR-30): date=$date createdOn=${habit.createdOn}"
        }
        return when (habit.type) {
            HabitType.POSITIVE -> when (intent) {
                LogIntent.DONE ->
                    if (isActiveOn(habit, date)) DayStatus.DONE else DayStatus.BONUS
                LogIntent.CLEAN, LogIntent.SLIPPED ->
                    throw IllegalArgumentException("Positive habits accept only DONE taps (FR-5)")
            }
            HabitType.NEGATIVE -> when (intent) {
                LogIntent.CLEAN -> DayStatus.CLEAN
                LogIntent.SLIPPED -> DayStatus.SLIPPED
                LogIntent.DONE ->
                    throw IllegalArgumentException("Negative habits accept only No/Yes taps (FR-6)")
            }
        }
    }
}
