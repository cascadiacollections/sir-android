package com.cascadiacollections.sir

import androidx.media3.common.Player
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class IsActuallyPlayingExtensionTest {

    private fun mockPlayer(playWhenReady: Boolean, playbackState: Int): Player = mockk {
        every { this@mockk.playWhenReady } returns playWhenReady
        every { this@mockk.playbackState } returns playbackState
    }

    @Test
    fun `isActuallyPlaying true when playWhenReady and STATE_READY`() {
        val player = mockPlayer(playWhenReady = true, playbackState = Player.STATE_READY)
        assertThat(player.isActuallyPlaying).isTrue()
    }

    @Test
    fun `isActuallyPlaying false when playWhenReady but STATE_BUFFERING`() {
        val player = mockPlayer(playWhenReady = true, playbackState = Player.STATE_BUFFERING)
        assertThat(player.isActuallyPlaying).isFalse()
    }

    @Test
    fun `isActuallyPlaying false when not playWhenReady and STATE_READY`() {
        val player = mockPlayer(playWhenReady = false, playbackState = Player.STATE_READY)
        assertThat(player.isActuallyPlaying).isFalse()
    }

    @Test
    fun `isActuallyPlaying false when not playWhenReady and STATE_IDLE`() {
        val player = mockPlayer(playWhenReady = false, playbackState = Player.STATE_IDLE)
        assertThat(player.isActuallyPlaying).isFalse()
    }
}
