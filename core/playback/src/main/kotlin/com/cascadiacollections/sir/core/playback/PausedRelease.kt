package com.cascadiacollections.sir.core.playback

/**
 * Releases a stream that has sat paused for too long.
 *
 * A paused live stream keeps its connection open (and the server keeps sending into it)
 * for as long as the player stays prepared, and resuming it after a long pause plays audio
 * that is minutes stale. ShoutKit drops the stream after ten minutes paused; pressing play
 * afterwards rejoins the live stream from scratch.
 *
 * Pure policy with an injected [clock] (milliseconds, monotonic — `elapsedRealtime` in the
 * service): the caller schedules its own check after [onPaused]'s delay, and releases only
 * if [isDue] still says so when it fires. Playing again cancels via [onPlay].
 */
class PausedRelease(private val clock: () -> Long, private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    init {
        require(timeoutMs > 0) { "timeoutMs must be positive" }
    }

    private var pausedAtMs: Long? = null

    /** Whether a pause is currently being timed. */
    val isArmed: Boolean get() = pausedAtMs != null

    /**
     * Starts timing a pause, if one isn't already being timed, and returns how long the
     * caller should wait before checking [isDue]. A repeated pause keeps the original start:
     * pausing an already paused stream doesn't buy it more time.
     */
    fun onPaused(): Long {
        val now = clock()
        val since = pausedAtMs ?: now.also { pausedAtMs = it }
        return (timeoutMs - (now - since)).coerceAtLeast(0L)
    }

    /** Playback resumed (or the stream was replaced): nothing is due any more. */
    fun onPlay() {
        pausedAtMs = null
    }

    /** Whether the stream has been paused for at least the timeout. */
    fun isDue(): Boolean {
        val since = pausedAtMs ?: return false
        return clock() - since >= timeoutMs
    }

    /** The caller released the stream; disarms until the next pause. */
    fun onReleased() {
        pausedAtMs = null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 10 * 60 * 1_000L
    }
}
