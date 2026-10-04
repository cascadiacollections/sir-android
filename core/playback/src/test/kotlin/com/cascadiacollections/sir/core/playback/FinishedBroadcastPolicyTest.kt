package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishedBroadcastPolicyTest {

    @Test
    fun `a file of known duration is finite`() {
        assertTrue(FinishedBroadcastPolicy.isFinite(isLive = false, isDynamic = false, durationMs = 300_000L))
    }

    @Test
    fun `live, dynamic or unknown-length media is not finite`() {
        assertFalse(FinishedBroadcastPolicy.isFinite(isLive = true, isDynamic = false, durationMs = 300_000L))
        assertFalse(FinishedBroadcastPolicy.isFinite(isLive = false, isDynamic = true, durationMs = 300_000L))
        assertFalse(FinishedBroadcastPolicy.isFinite(isLive = false, isDynamic = false, durationMs = null))
        assertFalse(FinishedBroadcastPolicy.isFinite(isLive = false, isDynamic = false, durationMs = 0L))
    }

    @Test
    fun `a live stream that ends is rejoined whatever the setting`() {
        assertEquals(
            EndOfStreamAction.REJOIN,
            FinishedBroadcastPolicy.onEnded(isFinite = false, loopFinishedBroadcasts = false)
        )
        assertEquals(
            EndOfStreamAction.REJOIN,
            FinishedBroadcastPolicy.onEnded(isFinite = false, loopFinishedBroadcasts = true)
        )
    }

    @Test
    fun `a finished broadcast stops by default and loops when asked`() {
        assertEquals(
            EndOfStreamAction.STOP,
            FinishedBroadcastPolicy.onEnded(isFinite = true, loopFinishedBroadcasts = false)
        )
        assertEquals(
            EndOfStreamAction.LOOP,
            FinishedBroadcastPolicy.onEnded(isFinite = true, loopFinishedBroadcasts = true)
        )
    }

    @Test
    fun `only a finite item with looping on repeats itself`() {
        assertTrue(FinishedBroadcastPolicy.repeatsCurrentItem(isFinite = true, loopFinishedBroadcasts = true))
        assertFalse(FinishedBroadcastPolicy.repeatsCurrentItem(isFinite = true, loopFinishedBroadcasts = false))
        assertFalse(FinishedBroadcastPolicy.repeatsCurrentItem(isFinite = false, loopFinishedBroadcasts = true))
    }
}
