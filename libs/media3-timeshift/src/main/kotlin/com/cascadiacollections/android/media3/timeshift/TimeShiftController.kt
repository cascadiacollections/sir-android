package com.cascadiacollections.android.media3.timeshift

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Stable handle for driving DVR-style time-shift on a live stream.
 *
 * A controller owns the [CircularByteBuffer] that a [TimeShiftDataSource] streams into,
 * and outlives the individual data sources the player creates and discards — so callers
 * hold one controller for the lifetime of a stream instead of chasing the most recently
 * created data source.
 *
 * Positions are expressed as [Duration] and converted using [bytesPerSecond]. For the
 * constant-bitrate audio streams this is built for that conversion is exact; for
 * variable-bitrate sources it is an estimate.
 *
 * @param capacityBytes Size of the replay buffer. Divided by [bytesPerSecond] this is
 *   the maximum replay window.
 * @param bytesPerSecond Stream byte rate, i.e. bitrate in bits per second divided by 8.
 */
class TimeShiftController(
    capacityBytes: Int,
    private val bytesPerSecond: Int
) {

    init {
        require(bytesPerSecond > 0) { "bytesPerSecond must be positive" }
    }

    internal val buffer = CircularByteBuffer(capacityBytes)

    /** The longest replay window this controller can offer, once fully buffered. */
    val maxSeekBack: Duration = bytesToDuration(buffer.capacity)

    /** How far back playback can currently be seeked. Grows as the stream buffers. */
    val availableSeekBack: Duration
        get() = bytesToDuration(buffer.seekBackAvailable())

    /** True while playback is at the live edge. */
    val isLive: Boolean
        get() = buffer.isLive()

    /** True when at least [duration] of already-played audio can be replayed. */
    fun canSeekBack(duration: Duration): Boolean =
        buffer.canSeekBack(durationToBytes(duration))

    /**
     * Move playback backward by [duration].
     *
     * @return true if the full [duration] was buffered and the seek was applied;
     *   false if not enough audio is buffered yet, in which case playback is unchanged.
     */
    fun seekBack(duration: Duration): Boolean {
        val bytes = durationToBytes(duration)
        if (!buffer.canSeekBack(bytes)) return false
        buffer.seekBack(bytes)
        return true
    }

    /** Return playback to the live edge, discarding the time-shift delay. */
    fun goLive() {
        buffer.goLive()
    }

    /** Drop all buffered audio, e.g. when switching to a different stream. */
    fun reset() {
        buffer.clear()
    }

    private fun durationToBytes(duration: Duration): Int {
        if (duration <= Duration.ZERO) return 0
        val bytes = duration.inWholeMilliseconds * bytesPerSecond / 1000L
        return bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun bytesToDuration(bytes: Int): Duration =
        (bytes.toDouble() / bytesPerSecond).seconds
}
