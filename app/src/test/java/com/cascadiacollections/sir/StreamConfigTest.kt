package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import com.cascadiacollections.sir.core.playback.StreamConfig
import com.cascadiacollections.sir.core.playback.StreamQuality
import org.junit.Test

class StreamConfigTest {

    @Test
    fun `DEFAULT_STREAM_URL is valid HTTPS`() {
        assertThat(StreamConfig.DEFAULT_STREAM_URL).startsWith("https://")
    }

    @Test
    fun `DEFAULT_STREAM_URL is not blank`() {
        assertThat(StreamConfig.DEFAULT_STREAM_URL.isNotBlank()).isTrue()
    }

    @Test
    fun `all StreamQuality entries use DEFAULT_STREAM_URL`() {
        StreamQuality.entries.forEach { quality ->
            assertThat(quality.url, name = "${quality.name} URL doesn't match DEFAULT_STREAM_URL")
                .isEqualTo(StreamConfig.DEFAULT_STREAM_URL)
        }
    }

    @Test
    fun `fallback test stream list is not empty`() {
        assertThat(StreamConfig.FALLBACK_TEST_STREAMS).isNotEmpty()
    }

    @Test
    fun `fallback test streams are valid HTTPS URLs`() {
        StreamConfig.FALLBACK_TEST_STREAMS.forEach { source ->
            val url = java.net.URL(source.url)
            assertThat(source.name.isNotBlank()).isTrue()
            assertThat(url.protocol).isEqualTo("https")
            assertThat(url.host.isNotBlank()).isTrue()
        }
    }
}
