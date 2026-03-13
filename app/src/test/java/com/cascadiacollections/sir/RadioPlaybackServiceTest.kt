package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
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
}
