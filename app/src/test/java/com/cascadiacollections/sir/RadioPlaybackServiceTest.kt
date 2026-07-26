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
        assertTrue(
            RadioPlaybackService.ACTION_SEEK_BACK.startsWith(prefix)
        )
        assertTrue(
            RadioPlaybackService.ACTION_GO_LIVE.startsWith(prefix)
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
        assertTrue(RadioPlaybackService.ACTION_SEEK_BACK.endsWith("SEEK_BACK"))
        assertTrue(RadioPlaybackService.ACTION_GO_LIVE.endsWith("GO_LIVE"))
    }

    @Test
    fun `action constants are distinct`() {
        val actions = setOf(
            RadioPlaybackService.ACTION_SET_SLEEP_TIMER,
            RadioPlaybackService.ACTION_SET_EQUALIZER,
            RadioPlaybackService.ACTION_SEEK_BACK,
            RadioPlaybackService.ACTION_GO_LIVE
        )
        assertEquals(4, actions.size)
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
            RadioPlaybackService.ACTION_SET_EQUALIZER,
            RadioPlaybackService.ACTION_SEEK_BACK,
            RadioPlaybackService.ACTION_GO_LIVE
        )
        val extras = setOf(
            RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES,
            RadioPlaybackService.EXTRA_EQUALIZER_PRESET
        )
        assertTrue("Actions and extras must not share identical strings", actions.none { it in extras })
    }

    @Test
    fun `REPLAY_BUFFER_SIZE holds at least 30 seconds at 64kbps`() {
        val bytesFor30Seconds = 30 * 8_000  // 64kbps = 8KB/s
        assertTrue(
            "Buffer (${ RadioPlaybackService.REPLAY_BUFFER_SIZE }) must hold >= 30s ($bytesFor30Seconds bytes)",
            RadioPlaybackService.REPLAY_BUFFER_SIZE >= bytesFor30Seconds
        )
    }
}
