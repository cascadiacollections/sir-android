package com.cascadiacollections.sir.core.playback

/**
 * Exponential backoff for reconnecting a dropped stream.
 *
 * Live radio drops are usually transient (a mirror restarting, a mobile handover), so
 * the first retries are fast; the cap stops a station that is genuinely offline from
 * holding a wake lock and hammering the server.
 */
class RetryBackoff(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must not be negative" }
        require(initialDelayMs > 0) { "initialDelayMs must be positive" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
    }

    var attempt: Int = 0
        private set

    /**
     * Human-readable "attempt 2/5" for logs.
     *
     * The numerator is capped at [maxRetries] because the playback service logs this
     * before deciding whether another retry will happen, so the final failure used to
     * report an impossible "6/5".
     */
    val attemptLabel: String get() = "${(attempt + 1).coerceAtMost(maxRetries)}/$maxRetries"

    /**
     * Returns the delay before the next retry, or null when retries are exhausted and
     * the caller should surface an error instead.
     */
    fun nextDelayMs(): Long? {
        if (attempt >= maxRetries) return null
        val delay = delayForAttempt(attempt)
        attempt++
        return delay
    }

    /**
     * `initialDelayMs * 2^attempt`, capped at [maxDelayMs].
     *
     * Computed by repeated doubling rather than `initialDelayMs shl attempt`:
     * [maxRetries] is caller-supplied and unbounded, and `Long.shl` masks its operand to
     * six bits, so a large attempt count silently wrapped the shift and could yield a
     * *shorter* — or, once the multiplication overflowed, a negative — delay than the
     * attempt before it. Doubling returns at the cap, so this runs at most
     * log2(maxDelayMs / initialDelayMs) times regardless of how large [attempt] gets.
     */
    private fun delayForAttempt(attempt: Int): Long {
        var delay = initialDelayMs
        repeat(attempt) {
            if (delay > maxDelayMs / 2) return maxDelayMs
            delay *= 2
        }
        return delay
    }

    /** Call once playback is healthy again so the next drop starts from a fast retry. */
    fun reset() {
        attempt = 0
    }

    companion object {
        const val DEFAULT_MAX_RETRIES: Int = 5
        const val DEFAULT_INITIAL_DELAY_MS: Long = 2_000L
        const val DEFAULT_MAX_DELAY_MS: Long = 30_000L
    }
}
