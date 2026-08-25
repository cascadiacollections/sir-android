package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.playback.StreamConfig
import com.cascadiacollections.sir.core.playback.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [StreamQuality] enum.
 */
class StreamQualityTest {

    @Test
    fun `fromOrdinal returns correct StreamQuality for valid ordinals`() {
        assertEquals(StreamQuality.HIGH, StreamQuality.fromOrdinal(0))
        assertEquals(StreamQuality.MEDIUM, StreamQuality.fromOrdinal(1))
        assertEquals(StreamQuality.LOW, StreamQuality.fromOrdinal(2))
    }

    @Test
    fun `fromOrdinal returns HIGH for out-of-bounds ordinals`() {
        assertEquals(StreamQuality.HIGH, StreamQuality.fromOrdinal(-1))
        assertEquals(StreamQuality.HIGH, StreamQuality.fromOrdinal(3))
        assertEquals(StreamQuality.HIGH, StreamQuality.fromOrdinal(100))
        assertEquals(StreamQuality.HIGH, StreamQuality.fromOrdinal(Int.MAX_VALUE))
    }

    @Test
    fun `StreamQuality has exactly 3 entries`() {
        assertEquals(3, StreamQuality.entries.size)
    }

    @Test
    fun `StreamQuality labelRes are all valid resource ids`() {
        StreamQuality.entries.forEach { quality ->
            assertTrue("labelRes for $quality should be a valid resource id", quality.labelRes != 0)
        }
    }

    @Test
    fun `StreamQuality labelRes are all unique`() {
        val labelResIds = StreamQuality.entries.map { it.labelRes }
        assertEquals(labelResIds.size, labelResIds.toSet().size)
    }

    @Test
    fun `StreamQuality URLs are valid HTTPS`() {
        StreamQuality.entries.forEach { quality ->
            assertTrue(
                "URL for $quality should start with https://",
                quality.url.startsWith("https://")
            )
        }
    }

    @Test
    fun `StreamQuality ordinal stability`() {
        assertEquals(0, StreamQuality.HIGH.ordinal)
        assertEquals(1, StreamQuality.MEDIUM.ordinal)
        assertEquals(2, StreamQuality.LOW.ordinal)
    }

    @Test
    fun `all qualities use StreamConfig DEFAULT_STREAM_URL`() {
        StreamQuality.entries.forEach { quality ->
            assertEquals(StreamConfig.DEFAULT_STREAM_URL, quality.url)
        }
    }
}
