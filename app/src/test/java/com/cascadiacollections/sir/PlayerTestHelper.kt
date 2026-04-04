package com.cascadiacollections.sir

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk

/**
 * Factory for a pre-stubbed MockK [Player] with sensible defaults.
 * Avoids repeating 10+ `every {}` stubs in every test file.
 */
object PlayerTestHelper {

    fun createMockPlayer(
        isPlaying: Boolean = false,
        playWhenReady: Boolean = false,
        playbackState: Int = Player.STATE_IDLE
    ): Player = mockk(relaxed = true) {
        every { this@mockk.isPlaying } returns isPlaying
        every { this@mockk.playWhenReady } returns playWhenReady
        every { this@mockk.playbackState } returns playbackState
        every { this@mockk.mediaMetadata } returns MediaMetadata.EMPTY
    }
}
