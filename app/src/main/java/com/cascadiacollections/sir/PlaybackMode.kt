package com.cascadiacollections.sir

internal sealed interface PlaybackMode {
    data object Live : PlaybackMode
    data object TimeShifted : PlaybackMode
}
