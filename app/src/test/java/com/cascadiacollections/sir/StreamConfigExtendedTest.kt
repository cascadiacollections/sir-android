package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.startsWith
import com.cascadiacollections.sir.core.playback.StreamConfig
import org.junit.Test

/**
 * Tests for [StreamConfig] constants.
 */
class StreamConfigExtendedTest {

    @Test
    fun `DEFAULT_STREAM_URL is HTTPS`() {
        assertThat(StreamConfig.DEFAULT_STREAM_URL).startsWith("https://")
    }

    @Test
    fun `DEFAULT_STREAM_URL is not empty`() {
        assertThat(StreamConfig.DEFAULT_STREAM_URL).isNotEmpty()
    }

    @Test
    fun `DEFAULT_STREAM_URL contains stream path`() {
        assertThat(StreamConfig.DEFAULT_STREAM_URL).contains("/stream")
    }

    @Test
    fun `DEFAULT_STREAM_URL is a valid URL format`() {
        val url = java.net.URL(StreamConfig.DEFAULT_STREAM_URL)
        assertThat(url.protocol).isEqualTo("https")
        assertThat(url.host).isNotEmpty()
    }

    @Test
    fun `fallback stream URLs are unique`() {
        val urls = StreamConfig.FALLBACK_TEST_STREAMS.map { it.url }
        assertThat(urls.toSet()).hasSize(urls.size)
    }
}
