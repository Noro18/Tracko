package com.example.tracko.domain.model

/**
 * What happened to one habit on one day.
 * Exactly the 8 statuses from the data model §2.2 — this is the column
 * vocabulary of habit_log.status, and the language the streak engine speaks.
 */
enum class DayStatus {
    /** Positive habit completed (you tapped Done). */
    DONE,

    /** Negative habit, no slip. Answered by you, or written by the system if you never answered. */
    CLEAN,

    /** Negative habit, you slipped (you tapped Yes). */
    SLIPPED,

    /** Positive habit not done — written by the system at finalization. */
    MISSED,

    /** A miss or slip covered by a freeze — written by the system at finalization. */
    FROZEN,

    /** The habit doesn't apply that weekday — written by the system at finalization. */
    NOT_ACTIVE,

    /** The habit was paused that day — written by the system. */
    PAUSED,

    /** Positive habit done on a day it wasn't scheduled (FR-31). */
    BONUS,
}
