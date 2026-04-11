package com.cascadiacollections.sir

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
    fun `StreamQuality labels are all non-blank`() {
        StreamQuality.entries.forEach { quality ->
            assertTrue("Label for $quality should not be blank", quality.label.isNotBlank())
        }
    }

    @Test
    fun `StreamQuality labels are all unique`() {
        val labels = StreamQuality.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `StreamQuality URLs are valid HTTPS`() {
        StreamQuality.entries.forEach { quality ->
            assertTrue(
                "URL for ${quality.label} should start with https://",
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
