package com.cascadiacollections.sir

import org.junit.Assert.assertTrue
import org.junit.Test

class StreamConfigTest {

    @Test
    fun `DEFAULT_STREAM_URL is valid HTTPS`() {
        assertTrue(StreamConfig.DEFAULT_STREAM_URL.startsWith("https://"))
    }

    @Test
    fun `DEFAULT_STREAM_URL is not blank`() {
        assertTrue(StreamConfig.DEFAULT_STREAM_URL.isNotBlank())
    }

    @Test
    fun `all StreamQuality entries use DEFAULT_STREAM_URL`() {
        StreamQuality.entries.forEach { quality ->
            assertTrue(
                "${quality.name} URL doesn't match DEFAULT_STREAM_URL",
                quality.url == StreamConfig.DEFAULT_STREAM_URL
            )
        }
    }
}
