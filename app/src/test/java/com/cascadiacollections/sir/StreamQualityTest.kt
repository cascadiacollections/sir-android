package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.startsWith
import com.cascadiacollections.sir.core.playback.StreamConfig
import com.cascadiacollections.sir.core.playback.StreamQuality
import org.junit.Test

/**
 * Tests for [StreamQuality] enum.
 */
class StreamQualityTest {

    @Test
    fun `fromOrdinal returns correct StreamQuality for valid ordinals`() {
        assertThat(StreamQuality.fromOrdinal(0)).isEqualTo(StreamQuality.HIGH)
        assertThat(StreamQuality.fromOrdinal(1)).isEqualTo(StreamQuality.MEDIUM)
        assertThat(StreamQuality.fromOrdinal(2)).isEqualTo(StreamQuality.LOW)
    }

    @Test
    fun `fromOrdinal returns HIGH for out-of-bounds ordinals`() {
        assertThat(StreamQuality.fromOrdinal(-1)).isEqualTo(StreamQuality.HIGH)
        assertThat(StreamQuality.fromOrdinal(3)).isEqualTo(StreamQuality.HIGH)
        assertThat(StreamQuality.fromOrdinal(100)).isEqualTo(StreamQuality.HIGH)
        assertThat(StreamQuality.fromOrdinal(Int.MAX_VALUE)).isEqualTo(StreamQuality.HIGH)
    }

    @Test
    fun `StreamQuality has exactly 3 entries`() {
        assertThat(StreamQuality.entries).hasSize(3)
    }

    @Test
    fun `StreamQuality labelRes are all valid resource ids`() {
        StreamQuality.entries.forEach { quality ->
            assertThat(quality.labelRes, name = "labelRes for $quality should be a valid resource id").isNotEqualTo(0)
        }
    }

    @Test
    fun `StreamQuality labelRes are all unique`() {
        val labelResIds = StreamQuality.entries.map { it.labelRes }
        assertThat(labelResIds.toSet()).hasSize(labelResIds.size)
    }

    @Test
    fun `StreamQuality URLs are valid HTTPS`() {
        StreamQuality.entries.forEach { quality ->
            assertThat(quality.url, name = "URL for $quality should start with https://").startsWith("https://")
        }
    }

    @Test
    fun `StreamQuality ordinal stability`() {
        assertThat(StreamQuality.HIGH.ordinal).isEqualTo(0)
        assertThat(StreamQuality.MEDIUM.ordinal).isEqualTo(1)
        assertThat(StreamQuality.LOW.ordinal).isEqualTo(2)
    }

    @Test
    fun `all qualities use StreamConfig DEFAULT_STREAM_URL`() {
        StreamQuality.entries.forEach { quality ->
            assertThat(quality.url).isEqualTo(StreamConfig.DEFAULT_STREAM_URL)
        }
    }
}
