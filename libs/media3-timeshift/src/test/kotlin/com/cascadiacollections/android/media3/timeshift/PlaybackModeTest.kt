package com.cascadiacollections.android.media3.timeshift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeTest {

    @Test
    fun `Live is a singleton`() {
        assertSame(PlaybackMode.Live, PlaybackMode.Live)
    }

    @Test
    fun `TimeShifted is a singleton`() {
        assertSame(PlaybackMode.TimeShifted, PlaybackMode.TimeShifted)
    }

    @Test
    fun `Live and TimeShifted are different types`() {
        assertNotSame(PlaybackMode.Live, PlaybackMode.TimeShifted)
    }

    @Test
    fun `both implement PlaybackMode`() {
        assertTrue(PlaybackMode.Live is PlaybackMode)
        assertTrue(PlaybackMode.TimeShifted is PlaybackMode)
    }

    @Test
    fun `exhaustive when covers all cases`() {
        val modes: List<PlaybackMode> = listOf(PlaybackMode.Live, PlaybackMode.TimeShifted)
        modes.forEach { mode ->
            val label = when (mode) {
                PlaybackMode.Live -> "live"
                PlaybackMode.TimeShifted -> "shifted"
            }
            assertTrue(label.isNotEmpty())
        }
    }

    @Test
    fun `toString produces meaningful names`() {
        assertTrue(PlaybackMode.Live.toString().contains("Live"))
        assertTrue(PlaybackMode.TimeShifted.toString().contains("TimeShifted"))
    }
}
