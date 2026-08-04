package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutePolicyTest {

    @Test
    fun `noisy while playing pauses`() {
        val policy = AudioRoutePolicy()
        assertTrue(policy.onBecomingNoisy(isPlaying = true))
        assertTrue(policy.pausedByRouteLoss)
    }

    @Test
    fun `noisy while already paused does nothing`() {
        val policy = AudioRoutePolicy()
        assertFalse(policy.onBecomingNoisy(isPlaying = false))
        assertFalse(policy.pausedByRouteLoss)
    }

    @Test
    fun `route restored resumes only what we paused`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        assertTrue(policy.onRouteRestored())
        assertFalse(policy.pausedByRouteLoss)
    }

    @Test
    fun `route restored without a route loss does not resume`() {
        assertFalse(AudioRoutePolicy().onRouteRestored())
    }

    @Test
    fun `route restored twice only resumes once`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        assertTrue(policy.onRouteRestored())
        assertFalse(policy.onRouteRestored())
    }

    @Test
    fun `user pause cancels the resume claim`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        policy.onPlaybackStateChangedByUser()
        assertFalse(policy.onRouteRestored())
    }

    @Test
    fun `resuming playback releases the claim`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        policy.onPlaybackStarted()
        assertFalse(policy.pausedByRouteLoss)
        assertFalse(policy.onRouteRestored())
    }

    /**
     * The regression this class exists to prevent: unplug, resume by hand on the speaker,
     * pause on purpose, then reconnect. The reconnect must stay silent.
     */
    @Test
    fun `reconnect does not resume a pause taken after a manual resume`() {
        val policy = AudioRoutePolicy()

        // Headphones pulled while playing — we own this pause.
        assertTrue(policy.onBecomingNoisy(isPlaying = true))

        // User presses play again and listens on the speaker.
        policy.onPlaybackStarted()

        // User pauses on purpose. The claim is already gone, so nothing to release.
        assertFalse(policy.pausedByRouteLoss)

        // Headphones plugged back in hours later.
        assertFalse(policy.onRouteRestored())
    }

    @Test
    fun `route loss after a manual resume is claimed again`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        policy.onPlaybackStarted()

        // A second disconnect is a fresh claim, not a permanently spent one.
        assertTrue(policy.onBecomingNoisy(isPlaying = true))
        assertTrue(policy.onRouteRestored())
    }
}

class RetryBackoffTest {

    @Test
    fun `delays double and are capped`() {
        val backoff = RetryBackoff()
        assertEquals(2_000L, backoff.nextDelayMs())
        assertEquals(4_000L, backoff.nextDelayMs())
        assertEquals(8_000L, backoff.nextDelayMs())
        assertEquals(16_000L, backoff.nextDelayMs())
        assertEquals(30_000L, backoff.nextDelayMs())
    }

    @Test
    fun `returns null once retries are exhausted`() {
        val backoff = RetryBackoff(maxRetries = 2)
        assertEquals(2_000L, backoff.nextDelayMs())
        assertEquals(4_000L, backoff.nextDelayMs())
        assertNull(backoff.nextDelayMs())
    }

    @Test
    fun `reset restarts from the first delay`() {
        val backoff = RetryBackoff(maxRetries = 1)
        assertEquals(2_000L, backoff.nextDelayMs())
        assertNull(backoff.nextDelayMs())
        backoff.reset()
        assertEquals(2_000L, backoff.nextDelayMs())
    }

    @Test
    fun `attempt label reports the upcoming attempt`() {
        val backoff = RetryBackoff(maxRetries = 5)
        assertEquals("1/5", backoff.attemptLabel)
        backoff.nextDelayMs()
        assertEquals("2/5", backoff.attemptLabel)
    }

    @Test
    fun `zero retries never retries`() {
        assertNull(RetryBackoff(maxRetries = 0).nextDelayMs())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `max delay below initial delay is rejected`() {
        RetryBackoff(initialDelayMs = 5_000L, maxDelayMs = 1_000L)
    }
}
