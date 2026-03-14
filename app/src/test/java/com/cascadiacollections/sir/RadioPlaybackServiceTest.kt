package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPlaybackServiceTest {

    @Test
    fun `action constants follow package naming convention`() {
        val prefix = "com.cascadiacollections.sir.action."
        assertTrue(
            RadioPlaybackService.ACTION_SET_SLEEP_TIMER.startsWith(prefix)
        )
        assertTrue(
            RadioPlaybackService.ACTION_SET_EQUALIZER.startsWith(prefix)
        )
    }

    @Test
    fun `extra key constants are non-empty`() {
        assertTrue(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES.isNotEmpty())
        assertTrue(RadioPlaybackService.EXTRA_EQUALIZER_PRESET.isNotEmpty())
    }

    @Test
    fun `CAST_MODULE_NAME matches settings gradle include`() {
        assertEquals("cast", CastFeatureManager.CAST_MODULE_NAME)
    }

    @Test
    fun `action constants have descriptive suffixes`() {
        assertTrue(RadioPlaybackService.ACTION_SET_SLEEP_TIMER.endsWith("SET_SLEEP_TIMER"))
        assertTrue(RadioPlaybackService.ACTION_SET_EQUALIZER.endsWith("SET_EQUALIZER"))
    }

    @Test
    fun `action constants are distinct`() {
        assertNotEquals(
            RadioPlaybackService.ACTION_SET_SLEEP_TIMER,
            RadioPlaybackService.ACTION_SET_EQUALIZER
        )
    }

    @Test
    fun `extra key constants are distinct`() {
        assertNotEquals(
            RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES,
            RadioPlaybackService.EXTRA_EQUALIZER_PRESET
        )
    }

    @Test
    fun `action and extra key constants do not overlap`() {
        val actions = setOf(
            RadioPlaybackService.ACTION_SET_SLEEP_TIMER,
            RadioPlaybackService.ACTION_SET_EQUALIZER
        )
        val extras = setOf(
            RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES,
            RadioPlaybackService.EXTRA_EQUALIZER_PRESET
        )
        assertTrue("Actions and extras must not share identical strings", actions.none { it in extras })
    }
}
