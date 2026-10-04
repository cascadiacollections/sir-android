package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.startsWith
import org.junit.Test

class RadioPlaybackServiceTest {

    @Test
    fun `action constants follow package naming convention`() {
        val prefix = "com.cascadiacollections.sir.action."
        assertThat(RadioPlaybackService.ACTION_SET_SLEEP_TIMER).startsWith(prefix)
        assertThat(RadioPlaybackService.ACTION_SET_EQUALIZER).startsWith(prefix)
        assertThat(RadioPlaybackService.ACTION_SEEK_BACK).startsWith(prefix)
        assertThat(RadioPlaybackService.ACTION_GO_LIVE).startsWith(prefix)
    }

    @Test
    fun `extra key constants are non-empty`() {
        assertThat(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES).isNotEmpty()
        assertThat(RadioPlaybackService.EXTRA_EQUALIZER_PRESET).isNotEmpty()
    }

    @Test
    fun `CAST_MODULE_NAME matches settings gradle include`() {
        assertThat(CastFeatureManager.CAST_MODULE_NAME).isEqualTo("cast")
    }

    @Test
    fun `action constants have descriptive suffixes`() {
        assertThat(RadioPlaybackService.ACTION_SET_SLEEP_TIMER).endsWith("SET_SLEEP_TIMER")
        assertThat(RadioPlaybackService.ACTION_SET_EQUALIZER).endsWith("SET_EQUALIZER")
        assertThat(RadioPlaybackService.ACTION_SEEK_BACK).endsWith("SEEK_BACK")
        assertThat(RadioPlaybackService.ACTION_GO_LIVE).endsWith("GO_LIVE")
    }

    @Test
    fun `action constants are distinct`() {
        val actions = setOf(
            RadioPlaybackService.ACTION_SET_SLEEP_TIMER,
            RadioPlaybackService.ACTION_SET_EQUALIZER,
            RadioPlaybackService.ACTION_SEEK_BACK,
            RadioPlaybackService.ACTION_GO_LIVE
        )
        assertThat(actions).hasSize(4)
    }

    @Test
    fun `extra key constants are distinct`() {
        assertThat(RadioPlaybackService.EXTRA_EQUALIZER_PRESET)
            .isNotEqualTo(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES)
    }

    @Test
    fun `action and extra key constants do not overlap`() {
        val actions = setOf(
            RadioPlaybackService.ACTION_SET_SLEEP_TIMER,
            RadioPlaybackService.ACTION_SET_EQUALIZER,
            RadioPlaybackService.ACTION_SEEK_BACK,
            RadioPlaybackService.ACTION_GO_LIVE
        )
        val extras = setOf(
            RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES,
            RadioPlaybackService.EXTRA_EQUALIZER_PRESET
        )
        assertThat(actions intersect extras, name = "Actions and extras must not share identical strings").isEmpty()
    }

    @Test
    fun `REPLAY_BUFFER_SIZE holds at least 30 seconds at 64kbps`() {
        val bytesFor30Seconds = 30 * 8_000 // 64kbps = 8KB/s
        assertThat(
            RadioPlaybackService.REPLAY_BUFFER_SIZE,
            name = "Buffer (${ RadioPlaybackService.REPLAY_BUFFER_SIZE }) must hold >= 30s ($bytesFor30Seconds bytes)"
        ).isGreaterThanOrEqualTo(bytesFor30Seconds)
    }
}
