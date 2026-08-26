package com.cascadiacollections.sir.core.playback

/**
 * Bounds how long a live stream may sit in `STATE_BUFFERING` before giving up.
 *
 * `DefaultLoadControl` never gives up on a stalled live stream on its own, and
 * `WAKE_MODE_NETWORK` keeps a wake lock and a WiFi lock held for as long as it keeps
 * trying — so a mount that accepts the connection and then sends nothing can rebuffer
 * indefinitely on a phone in a pocket. Mirrors ShoutKit's 90s stall ceiling.
 *
 * Pure policy with no timer of its own: the caller arms this on entering
 * `STATE_BUFFERING` while `playWhenReady`, clears it on `STATE_READY`, and schedules
 * its own callback after [timeoutMs] (the same pattern [RetryBackoff] uses for its
 * delays). The token [arm] returns guards that callback: if the stall clears, or a new
 * one is armed, before the callback fires, [isCurrent] tells the caller the callback is
 * stale and should be ignored.
 */
class StallCeiling(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {

    private var generation: Int = 0

    /** How long the caller should wait, from [arm], before checking [isCurrent]. */
    val timeoutDelayMs: Long get() = timeoutMs

    /** Arms the ceiling and returns a token identifying this arm. */
    fun arm(): Int = ++generation

    /** Clears the ceiling; a callback holding a token from before this becomes stale. */
    fun clear() {
        generation++
    }

    /** Whether [token] is still the most recent arm — i.e. not cleared or superseded. */
    fun isCurrent(token: Int): Boolean = token == generation

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 90_000L
    }
}
