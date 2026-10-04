package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class AudioRoutePolicyTest {

    @Test
    fun `noisy while playing pauses`() {
        val policy = AudioRoutePolicy()
        assertThat(policy.onBecomingNoisy(isPlaying = true)).isTrue()
        assertThat(policy.pausedByRouteLoss).isTrue()
    }

    @Test
    fun `noisy while already paused does nothing`() {
        val policy = AudioRoutePolicy()
        assertThat(policy.onBecomingNoisy(isPlaying = false)).isFalse()
        assertThat(policy.pausedByRouteLoss).isFalse()
    }

    @Test
    fun `route restored resumes only what we paused`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        assertThat(policy.onRouteRestored()).isTrue()
        assertThat(policy.pausedByRouteLoss).isFalse()
    }

    @Test
    fun `route restored without a route loss does not resume`() {
        assertThat(AudioRoutePolicy().onRouteRestored()).isFalse()
    }

    @Test
    fun `route restored twice only resumes once`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        assertThat(policy.onRouteRestored()).isTrue()
        assertThat(policy.onRouteRestored()).isFalse()
    }

    @Test
    fun `user pause cancels the resume claim`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        policy.onPlaybackStateChangedByUser()
        assertThat(policy.onRouteRestored()).isFalse()
    }

    @Test
    fun `resuming playback releases the claim`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        policy.onPlaybackStarted()
        assertThat(policy.pausedByRouteLoss).isFalse()
        assertThat(policy.onRouteRestored()).isFalse()
    }

    /**
     * The regression this class exists to prevent: unplug, resume by hand on the speaker,
     * pause on purpose, then reconnect. The reconnect must stay silent.
     */
    @Test
    fun `reconnect does not resume a pause taken after a manual resume`() {
        val policy = AudioRoutePolicy()

        // Headphones pulled while playing — we own this pause.
        assertThat(policy.onBecomingNoisy(isPlaying = true)).isTrue()

        // User presses play again and listens on the speaker.
        policy.onPlaybackStarted()

        // User pauses on purpose. The claim is already gone, so nothing to release.
        assertThat(policy.pausedByRouteLoss).isFalse()

        // Headphones plugged back in hours later.
        assertThat(policy.onRouteRestored()).isFalse()
    }

    @Test
    fun `route loss after a manual resume is claimed again`() {
        val policy = AudioRoutePolicy()
        policy.onBecomingNoisy(isPlaying = true)
        policy.onPlaybackStarted()

        // A second disconnect is a fresh claim, not a permanently spent one.
        assertThat(policy.onBecomingNoisy(isPlaying = true)).isTrue()
        assertThat(policy.onRouteRestored()).isTrue()
    }
}
