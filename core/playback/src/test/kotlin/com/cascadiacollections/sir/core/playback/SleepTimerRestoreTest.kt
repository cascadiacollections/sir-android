package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerRestoreTest {

    @Test
    fun `unset deadline restores nothing`() {
        assertNull(SleepTimerRestore.remainingMinutes(firesAtEpochMillis = 0L, nowEpochMillis = 1_000L))
    }

    @Test
    fun `expired deadline restores nothing`() {
        assertNull(SleepTimerRestore.remainingMinutes(1_000L, 2_000L))
    }

    @Test
    fun `remaining time rounds down to whole minutes`() {
        assertEquals(10, SleepTimerRestore.remainingMinutes(10 * 60_000L + 59_000L, 59_000L))
    }

    @Test
    fun `sub-minute remainder is kept alive for one minute`() {
        assertEquals(1, SleepTimerRestore.remainingMinutes(30_000L, 0L))
    }

    @Test
    fun `duration lookup falls back to off`() {
        assertEquals(SleepTimerDuration.THIRTY, SleepTimerDuration.fromMinutes(30))
        assertEquals(SleepTimerDuration.OFF, SleepTimerDuration.fromMinutes(7))
        assertTrue(SleepTimerDuration.THIRTY.isActive)
        assertTrue(!SleepTimerDuration.OFF.isActive)
    }
}

class PlaybackBufferConfigTest {

    @Test
    fun `live radio defaults are internally consistent`() {
        val config = PlaybackBufferConfig.LIVE_RADIO

        assertTrue(config.bufferForPlaybackMs <= config.minBufferMs)
        assertTrue(config.minBufferMs <= config.maxBufferMs)
        assertTrue(config.prioritizeTimeOverSizeThresholds)
    }

    @Test
    fun `max buffer below min buffer is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackBufferConfig(
                minBufferMs = 15_000,
                maxBufferMs = 10_000,
                bufferForPlaybackMs = 2_500,
                bufferForPlaybackAfterRebufferMs = 5_000
            )
        }
    }

    @Test
    fun `playback threshold above min buffer is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackBufferConfig(
                minBufferMs = 2_000,
                maxBufferMs = 60_000,
                bufferForPlaybackMs = 2_500,
                bufferForPlaybackAfterRebufferMs = 2_500
            )
        }
    }
}
