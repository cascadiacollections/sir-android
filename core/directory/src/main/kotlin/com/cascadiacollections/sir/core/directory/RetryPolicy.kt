package com.cascadiacollections.sir.core.directory

/**
 * Bounded mirror failover, matching ShoutKit's `RetryPolicy.interactive`: at most
 * [maxAttempts] requests per logical call, each on the next mirror, separated by an
 * exponential backoff of `baseDelayMs * 2^n` (350 ms, 700 ms, ...).
 *
 * The whole run is additionally capped by the directory's wall-clock failover budget,
 * so the backoff can never push a search past it.
 */
data class RetryPolicy(val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS, val baseDelayMs: Long = DEFAULT_BASE_DELAY_MS) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(baseDelayMs >= 0) { "baseDelayMs must not be negative" }
    }

    /** Delay before the attempt following the zero-based [attempt]. */
    fun delayAfter(attempt: Int): Long = baseDelayMs shl attempt.coerceIn(0, MAX_SHIFT)

    companion object {
        const val DEFAULT_MAX_ATTEMPTS: Int = 3
        const val DEFAULT_BASE_DELAY_MS: Long = 350
        private const val MAX_SHIFT = 16

        val DEFAULT: RetryPolicy = RetryPolicy()
    }
}
