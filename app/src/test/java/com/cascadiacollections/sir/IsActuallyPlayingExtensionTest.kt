package com.cascadiacollections.sir

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsActuallyPlayingExtensionTest {

    private fun mockPlayer(playWhenReady: Boolean, playbackState: Int): Player =
        mockk {
            every { this@mockk.playWhenReady } returns playWhenReady
            every { this@mockk.playbackState } returns playbackState
        }

    @Test
    fun `isActuallyPlaying true when playWhenReady and STATE_READY`() {
        val player = mockPlayer(playWhenReady = true, playbackState = Player.STATE_READY)
        assertTrue(player.isActuallyPlaying)
    }

    @Test
    fun `isActuallyPlaying false when playWhenReady but STATE_BUFFERING`() {
        val player = mockPlayer(playWhenReady = true, playbackState = Player.STATE_BUFFERING)
        assertFalse(player.isActuallyPlaying)
    }

    @Test
    fun `isActuallyPlaying false when not playWhenReady and STATE_READY`() {
        val player = mockPlayer(playWhenReady = false, playbackState = Player.STATE_READY)
        assertFalse(player.isActuallyPlaying)
    }

    @Test
    fun `isActuallyPlaying false when not playWhenReady and STATE_IDLE`() {
        val player = mockPlayer(playWhenReady = false, playbackState = Player.STATE_IDLE)
        assertFalse(player.isActuallyPlaying)
    }
}
