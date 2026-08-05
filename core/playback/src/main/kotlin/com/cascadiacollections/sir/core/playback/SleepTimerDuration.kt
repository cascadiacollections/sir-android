package com.cascadiacollections.sir.core.playback

import androidx.annotation.StringRes

/**
 * Available sleep timer durations, in minutes.
 */
enum class SleepTimerDuration(val minutes: Int, @StringRes val labelRes: Int) {
    OFF(0, R.string.sleep_timer_duration_off),
    FIFTEEN(15, R.string.sleep_timer_duration_15m),
    THIRTY(30, R.string.sleep_timer_duration_30m),
    SIXTY(60, R.string.sleep_timer_duration_1h),
    NINETY(90, R.string.sleep_timer_duration_1h30m);

    val isActive: Boolean get() = minutes > 0

    companion object {
        fun fromMinutes(minutes: Int): SleepTimerDuration =
            entries.find { it.minutes == minutes } ?: OFF
    }
}

/**
 * Resolves how a persisted sleep timer deadline should be restored after a process
 * restart. Extracted from the playback service so the "is it still relevant, and how
 * much is left" decision is testable without a running service.
 */
object SleepTimerRestore {

    /**
     * @return the number of whole minutes still to run, or `null` when the persisted
     *   deadline has already passed (or was never set) and should be cleared.
     */
    fun remainingMinutes(firesAtEpochMillis: Long, nowEpochMillis: Long): Int? {
        if (firesAtEpochMillis <= 0L) return null
        val remainingMillis = firesAtEpochMillis - nowEpochMillis
        if (remainingMillis <= 0L) return null
        return (remainingMillis / 60_000L).toInt().coerceAtLeast(1)
    }
}
