package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class RetryBackoffTest {

    @Test
    fun `the default schedule is ShoutKit's three reconnects at 2, 4 and 8 seconds`() {
        val backoff = RetryBackoff()

        assertThat(backoff.drain()).containsExactly(2_000L, 4_000L, 8_000L)
        assertThat(backoff.nextDelayMs()).isNull()
    }

    @Test
    fun `delays double until the cap and then stop`() {
        val backoff = RetryBackoff(maxRetries = 5, maxDelayMs = 30_000L)

        // 2s, 4s, 8s, 16s, then 32s capped to the 30s ceiling.
        assertThat(backoff.drain()).containsExactly(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)
        assertThat(backoff.nextDelayMs()).isNull()
    }

    @Test
    fun `reset returns to the fastest retry`() {
        val backoff = RetryBackoff()
        repeat(3) { backoff.nextDelayMs() }

        backoff.reset()

        assertThat(backoff.attempt).isEqualTo(0)
        assertThat(backoff.nextDelayMs()).isEqualTo(2_000L)
    }

    @Test
    fun `a large retry budget never produces a shorter or negative delay`() {
        // maxRetries is caller-supplied and unbounded. `initialDelayMs shl attempt` masks
        // its operand to six bits, so attempt 64 wrapped back to a single-step shift and
        // handed out 2s again after having reached the 30s cap; higher attempts overflowed
        // the multiplication outright and went negative, which the cap did not catch.
        val delays = RetryBackoff(maxRetries = 70, maxDelayMs = 30_000L).drain()

        assertThat(delays).hasSize(70)
        assertThat(delays, name = "no delay may be negative").each { it.isGreaterThan(0L) }
        assertThat(delays, name = "no delay may exceed the cap")
            .each { it.isLessThanOrEqualTo(30_000L) }
        assertThat(
            delays.zipWithNext().all { (previous, next) -> next >= previous },
            name = "delays must never decrease"
        ).isTrue()
        assertThat(delays.last(), name = "the cap must be reached").isEqualTo(30_000L)
    }

    @Test
    fun `a huge ceiling does not overflow the doubling`() {
        val delays = RetryBackoff(
            maxRetries = 70,
            initialDelayMs = 1L,
            maxDelayMs = Long.MAX_VALUE
        ).drain()

        assertThat(delays).each { it.isGreaterThan(0L) }
        assertThat(delays.zipWithNext().all { (previous, next) -> next >= previous }).isTrue()
    }

    @Test
    fun `attempt label never reports more attempts than the budget allows`() {
        val backoff = RetryBackoff(maxRetries = 5)
        assertThat(backoff.attemptLabel).isEqualTo("1/5")

        backoff.drain()

        // Logged once more after the retries are spent; "6/5" would be nonsense.
        assertThat(backoff.attemptLabel).isEqualTo("5/5")
    }

    @Test
    fun `a zero retry budget yields no delays`() {
        val backoff = RetryBackoff(maxRetries = 0)

        assertThat(backoff.nextDelayMs()).isNull()
        assertThat(backoff.attemptLabel).isEqualTo("0/0")
    }

    /** Pulls delays until the budget is exhausted. */
    private fun RetryBackoff.drain(): List<Long> = generateSequence { nextDelayMs() }.toList()
}
