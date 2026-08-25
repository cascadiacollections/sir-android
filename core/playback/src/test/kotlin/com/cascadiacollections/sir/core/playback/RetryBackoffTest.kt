package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryBackoffTest {

    @Test
    fun `delays double until the cap and then stop`() {
        val backoff = RetryBackoff()

        // 2s, 4s, 8s, 16s, then 32s capped to the 30s ceiling.
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L), backoff.drain())
        assertNull(backoff.nextDelayMs())
    }

    @Test
    fun `reset returns to the fastest retry`() {
        val backoff = RetryBackoff()
        repeat(3) { backoff.nextDelayMs() }

        backoff.reset()

        assertEquals(0, backoff.attempt)
        assertEquals(2_000L, backoff.nextDelayMs())
    }

    @Test
    fun `a large retry budget never produces a shorter or negative delay`() {
        // maxRetries is caller-supplied and unbounded. `initialDelayMs shl attempt` masks
        // its operand to six bits, so attempt 64 wrapped back to a single-step shift and
        // handed out 2s again after having reached the 30s cap; higher attempts overflowed
        // the multiplication outright and went negative, which the cap did not catch.
        val delays = RetryBackoff(maxRetries = 70).drain()

        assertEquals(70, delays.size)
        assertTrue("no delay may be negative", delays.all { it > 0 })
        assertTrue("no delay may exceed the cap", delays.all { it <= 30_000L })
        assertTrue(
            "delays must never decrease",
            delays.zipWithNext().all { (previous, next) -> next >= previous }
        )
        assertTrue("the cap must be reached", delays.last() == 30_000L)
    }

    @Test
    fun `a huge ceiling does not overflow the doubling`() {
        val delays = RetryBackoff(
            maxRetries = 70,
            initialDelayMs = 1L,
            maxDelayMs = Long.MAX_VALUE
        ).drain()

        assertTrue(delays.all { it > 0 })
        assertTrue(delays.zipWithNext().all { (previous, next) -> next >= previous })
    }

    @Test
    fun `attempt label never reports more attempts than the budget allows`() {
        val backoff = RetryBackoff(maxRetries = 5)
        assertEquals("1/5", backoff.attemptLabel)

        backoff.drain()

        // Logged once more after the retries are spent; "6/5" would be nonsense.
        assertEquals("5/5", backoff.attemptLabel)
    }

    @Test
    fun `a zero retry budget yields no delays`() {
        val backoff = RetryBackoff(maxRetries = 0)

        assertNull(backoff.nextDelayMs())
        assertEquals("0/0", backoff.attemptLabel)
    }

    /** Pulls delays until the budget is exhausted. */
    private fun RetryBackoff.drain(): List<Long> =
        generateSequence { nextDelayMs() }.toList()
}
