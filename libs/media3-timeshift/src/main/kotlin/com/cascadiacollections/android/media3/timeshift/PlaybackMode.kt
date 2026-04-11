package com.cascadiacollections.android.media3.timeshift

/**
 * Playback mode for time-shifted live streams.
 */
sealed interface PlaybackMode {
    data object Live : PlaybackMode
    data object TimeShifted : PlaybackMode
}
