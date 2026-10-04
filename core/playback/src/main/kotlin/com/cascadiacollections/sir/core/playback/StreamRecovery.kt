package com.cascadiacollections.sir.core.playback

/**
 * The single budget for every automatic reconnect, shared by every way a stream can drop:
 * a player error, a live stream that ends, and a stall.
 *
 * Mirrors ShoutKit's `PlaybackController+Recovery`: up to [MAX_RECONNECTS] reconnects at
 * 2 s, 4 s and 8 s, then the failed state with Retry. Media3's own load-error retries are
 * set to [LOAD_RETRIES_PER_CONNECTION] (none) so they cannot stack underneath — previously
 * each of our reconnects wrapped the player's own retries and the totals multiplied (B4 in
 * `docs/audioplayer-dependency-synergies.md`). One layer owns recovery, the UI can say
 * "Reconnecting…" for every attempt, and the worst case is stated here rather than emergent.
 */
object ReconnectBudget {
    /** Automatic reconnects after the first connection attempt fails or drops. */
    const val MAX_RECONNECTS: Int = 3

    /** Retries Media3 makes inside one connection attempt before surfacing the error. */
    const val LOAD_RETRIES_PER_CONNECTION: Int = 0

    /** Connection attempts one drop can cost, end to end: the first one plus every reconnect. */
    const val MAX_CONNECTION_ATTEMPTS: Int = (LOAD_RETRIES_PER_CONNECTION + 1) * (MAX_RECONNECTS + 1)
}

/** What the playback service should do about a stream that just failed. */
sealed interface RecoveryDecision {
    /**
     * Nobody asked for audio — a cold-start prepare while paused, or a pause that raced the
     * failure. Nothing is reported and nothing is retried; the failure surfaces only if the
     * listener presses play, which starts a fresh attempt.
     */
    data object Ignore : RecoveryDecision

    /** Reconnect after [delayMs], showing "Reconnecting…" (and the last track) meanwhile. */
    data class Reconnect(val delayMs: Long) : RecoveryDecision

    /** Give up and show [failure] with Retry. */
    data class Fail(val failure: StreamFailure) : RecoveryDecision
}

/**
 * Turns a [StreamFailure] into a [RecoveryDecision], spending [backoff]'s budget.
 *
 * Pure policy: the caller schedules the reconnect, publishes the failure, and calls
 * [onRecovered] once audio is flowing again.
 */
class StreamRecovery(private val backoff: RetryBackoff = RetryBackoff()) {

    /** "attempt 2/3" for logs. */
    val attemptLabel: String get() = backoff.attemptLabel

    fun onFailure(failure: StreamFailure, playbackWanted: Boolean): RecoveryDecision {
        if (!playbackWanted) {
            // A later play is a new request with the whole budget ahead of it.
            backoff.reset()
            return RecoveryDecision.Ignore
        }
        if (!failure.isRetryable) {
            backoff.reset()
            return RecoveryDecision.Fail(failure)
        }
        val delayMs = backoff.nextDelayMs()
        if (delayMs == null) {
            // Spent. The listener's Retry starts over with the full budget.
            backoff.reset()
            return RecoveryDecision.Fail(failure)
        }
        return RecoveryDecision.Reconnect(delayMs)
    }

    /** Playback is healthy (or was deliberately stopped): the next drop starts from 2 s. */
    fun onRecovered() {
        backoff.reset()
    }
}
