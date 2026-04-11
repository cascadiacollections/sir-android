package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [StreamConfig] constants.
 */
class StreamConfigExtendedTest {

    @Test
    fun `DEFAULT_STREAM_URL is HTTPS`() {
        assertTrue(StreamConfig.DEFAULT_STREAM_URL.startsWith("https://"))
    }

    @Test
    fun `DEFAULT_STREAM_URL is not empty`() {
        assertTrue(StreamConfig.DEFAULT_STREAM_URL.isNotEmpty())
    }

    @Test
    fun `DEFAULT_STREAM_URL contains stream path`() {
        assertTrue(StreamConfig.DEFAULT_STREAM_URL.contains("/stream"))
    }

    @Test
    fun `DEFAULT_STREAM_URL is a valid URL format`() {
        val url = java.net.URL(StreamConfig.DEFAULT_STREAM_URL)
        assertEquals("https", url.protocol)
        assertTrue(url.host.isNotEmpty())
    }
}
