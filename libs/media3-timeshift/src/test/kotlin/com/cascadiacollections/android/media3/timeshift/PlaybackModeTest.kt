package com.cascadiacollections.android.media3.timeshift

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import org.junit.Test

class PlaybackModeTest {

    @Test
    fun `Live is a singleton`() {
        assertThat(PlaybackMode.Live).isSameInstanceAs(PlaybackMode.Live)
    }

    @Test
    fun `TimeShifted is a singleton`() {
        assertThat(PlaybackMode.TimeShifted).isSameInstanceAs(PlaybackMode.TimeShifted)
    }

    @Test
    fun `Live and TimeShifted are different types`() {
        assertThat(PlaybackMode.TimeShifted).isNotSameInstanceAs(PlaybackMode.Live)
    }

    @Test
    fun `both implement PlaybackMode`() {
        assertThat(PlaybackMode.Live).isInstanceOf<PlaybackMode>()
        assertThat(PlaybackMode.TimeShifted).isInstanceOf<PlaybackMode>()
    }

    @Test
    fun `exhaustive when covers all cases`() {
        val modes: List<PlaybackMode> = listOf(PlaybackMode.Live, PlaybackMode.TimeShifted)
        modes.forEach { mode ->
            val label = when (mode) {
                PlaybackMode.Live -> "live"
                PlaybackMode.TimeShifted -> "shifted"
            }
            assertThat(label).isNotEmpty()
        }
    }

    @Test
    fun `toString produces meaningful names`() {
        assertThat(PlaybackMode.Live.toString()).contains("Live")
        assertThat(PlaybackMode.TimeShifted.toString()).contains("TimeShifted")
    }
}
