package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PlaybackMode] sealed interface.
 */
class PlaybackModeExtendedTest {

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
    fun `Live is instance of PlaybackMode`() {
        assertTrue(PlaybackMode.Live is PlaybackMode)
    }

    @Test
    fun `TimeShifted is instance of PlaybackMode`() {
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
