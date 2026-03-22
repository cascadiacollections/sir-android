package com.cascadiacollections.sir

import org.junit.Assert.assertNotEquals
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
    fun `Live and TimeShifted are distinct`() {
        assertNotEquals(PlaybackMode.Live, PlaybackMode.TimeShifted)
    }

    @Test
    fun `both implement PlaybackMode`() {
        assertTrue(PlaybackMode.Live is PlaybackMode)
        assertTrue(PlaybackMode.TimeShifted is PlaybackMode)
    }
}
