package com.cascadiacollections.sir.core.playback

/**
 * Buffering policy for a live radio stream.
 *
 * These numbers used to be inline arguments to `DefaultLoadControl.Builder`, which
 * made them impossible to reason about or reuse from the Wear module. Expressed as
 * data they can be documented, validated and varied per stream bitrate.
 */
data class PlaybackBufferConfig(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val prioritizeTimeOverSizeThresholds: Boolean = true
) {
    init {
        require(minBufferMs > 0) { "minBufferMs must be positive" }
        require(maxBufferMs >= minBufferMs) { "maxBufferMs must be >= minBufferMs" }
        require(bufferForPlaybackMs in 1..minBufferMs) {
            "bufferForPlaybackMs must be within (0, minBufferMs]"
        }
        require(bufferForPlaybackAfterRebufferMs in bufferForPlaybackMs..minBufferMs) {
            "bufferForPlaybackAfterRebufferMs must be within [bufferForPlaybackMs, minBufferMs]"
        }
    }

    companion object {
        /**
         * Tuned for a ~64 kbps live stream (8 KB/s): a 15 s floor is ~120 KB, the 60 s
         * ceiling ~480 KB, and playback starts after 2.5 s so cold start stays snappy.
         */
        val LIVE_RADIO = PlaybackBufferConfig(
            minBufferMs = 15_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 2_500,
            bufferForPlaybackAfterRebufferMs = 5_000
        )
    }
}
