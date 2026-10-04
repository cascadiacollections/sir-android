package com.cascadiacollections.sir.core.playback

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class SleepTimerRestoreTest {

    @Test
    fun `unset deadline restores nothing`() {
        assertThat(SleepTimerRestore.remainingMinutes(firesAtEpochMillis = 0L, nowEpochMillis = 1_000L)).isNull()
    }

    @Test
    fun `expired deadline restores nothing`() {
        assertThat(SleepTimerRestore.remainingMinutes(1_000L, 2_000L)).isNull()
    }

    @Test
    fun `remaining time rounds down to whole minutes`() {
        assertThat(SleepTimerRestore.remainingMinutes(10 * 60_000L + 59_000L, 59_000L)).isEqualTo(10)
    }

    @Test
    fun `sub-minute remainder is kept alive for one minute`() {
        assertThat(SleepTimerRestore.remainingMinutes(30_000L, 0L)).isEqualTo(1)
    }

    @Test
    fun `duration lookup falls back to off`() {
        assertThat(SleepTimerDuration.fromMinutes(30)).isEqualTo(SleepTimerDuration.THIRTY)
        assertThat(SleepTimerDuration.fromMinutes(7)).isEqualTo(SleepTimerDuration.OFF)
        assertThat(SleepTimerDuration.THIRTY.isActive).isTrue()
        assertThat(!SleepTimerDuration.OFF.isActive).isTrue()
    }
}

class PlaybackBufferConfigTest {

    @Test
    fun `live radio defaults are internally consistent`() {
        val config = PlaybackBufferConfig.LIVE_RADIO

        assertThat(config.bufferForPlaybackMs).isLessThanOrEqualTo(config.minBufferMs)
        assertThat(config.minBufferMs).isLessThanOrEqualTo(config.maxBufferMs)
        assertThat(config.prioritizeTimeOverSizeThresholds).isTrue()
    }

    @Test
    fun `max buffer below min buffer is rejected`() {
        assertFailure {
            PlaybackBufferConfig(
                minBufferMs = 15_000,
                maxBufferMs = 10_000,
                bufferForPlaybackMs = 2_500,
                bufferForPlaybackAfterRebufferMs = 5_000
            )
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `playback threshold above min buffer is rejected`() {
        assertFailure {
            PlaybackBufferConfig(
                minBufferMs = 2_000,
                maxBufferMs = 60_000,
                bufferForPlaybackMs = 2_500,
                bufferForPlaybackAfterRebufferMs = 2_500
            )
        }.isInstanceOf<IllegalArgumentException>()
    }
}
