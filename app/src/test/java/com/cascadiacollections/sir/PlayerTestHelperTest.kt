package com.cascadiacollections.sir

import androidx.media3.common.Player
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extended tests for [PlayerTestHelper] and the [Player.isActuallyPlaying] extension.
 */
class PlayerTestHelperTest {

    @Test
    fun `createMockPlayer defaults are correct`() {
        val player = PlayerTestHelper.createMockPlayer()
        assertFalse(player.isPlaying)
        assertFalse(player.playWhenReady)
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertNotNull(player.mediaMetadata)
    }

    @Test
    fun `createMockPlayer with custom values`() {
        val player = PlayerTestHelper.createMockPlayer(
            isPlaying = true,
            playWhenReady = true,
            playbackState = Player.STATE_READY
        )
        assertTrue(player.isPlaying)
        assertTrue(player.playWhenReady)
        assertEquals(Player.STATE_READY, player.playbackState)
    }

    @Test
    fun `createMockPlayer with buffering state`() {
        val player = PlayerTestHelper.createMockPlayer(
            playbackState = Player.STATE_BUFFERING
        )
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
    }

    @Test
    fun `createMockPlayer with ended state`() {
        val player = PlayerTestHelper.createMockPlayer(
            playbackState = Player.STATE_ENDED
        )
        assertEquals(Player.STATE_ENDED, player.playbackState)
    }
}
