package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamSourceResolverTest {

    private val station = StreamSource("https://example.com/station", "Station", "s1")
    private val quality = "https://example.com/quality"

    @Test
    fun `quality url is used when nothing else is set`() {
        val result = StreamSourceResolver.resolve(null, null, quality, defaultTitle = "SIR")

        assertEquals(StreamSource(quality, "SIR"), result)
    }

    @Test
    fun `selected station overrides the quality url`() {
        assertEquals(station, StreamSourceResolver.resolve(null, station, quality))
    }

    @Test
    fun `debug override beats the selected station`() {
        val result = StreamSourceResolver.resolve("https://example.com/debug", station, quality)

        assertEquals("https://example.com/debug", result.url)
        assertEquals(null, result.stationId)
    }

    @Test
    fun `blank overrides are ignored`() {
        assertEquals(station, StreamSourceResolver.resolve("   ", station, quality))
    }

    @Test
    fun `station without a url falls through to quality`() {
        val broken = StreamSource(url = "", title = "Broken")

        assertEquals(quality, StreamSourceResolver.resolve(null, broken, quality).url)
    }
}
