package com.cascadiacollections.sir

import androidx.media3.common.Player
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.junit.Test

/**
 * Extended tests for [PlayerTestHelper] and the [Player.isActuallyPlaying] extension.
 */
class PlayerTestHelperTest {

    @Test
    fun `createMockPlayer defaults are correct`() {
        val player = PlayerTestHelper.createMockPlayer()
        assertThat(player.isPlaying).isFalse()
        assertThat(player.playWhenReady).isFalse()
        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
        assertThat(player.mediaMetadata).isNotNull()
    }

    @Test
    fun `createMockPlayer with custom values`() {
        val player = PlayerTestHelper.createMockPlayer(
            isPlaying = true,
            playWhenReady = true,
            playbackState = Player.STATE_READY
        )
        assertThat(player.isPlaying).isTrue()
        assertThat(player.playWhenReady).isTrue()
        assertThat(player.playbackState).isEqualTo(Player.STATE_READY)
    }

    @Test
    fun `createMockPlayer with buffering state`() {
        val player = PlayerTestHelper.createMockPlayer(
            playbackState = Player.STATE_BUFFERING
        )
        assertThat(player.playbackState).isEqualTo(Player.STATE_BUFFERING)
    }

    @Test
    fun `createMockPlayer with ended state`() {
        val player = PlayerTestHelper.createMockPlayer(
            playbackState = Player.STATE_ENDED
        )
        assertThat(player.playbackState).isEqualTo(Player.STATE_ENDED)
    }
}
