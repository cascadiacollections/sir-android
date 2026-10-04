package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class StreamSourceResolverTest {

    private val station = StreamSource("https://example.com/station", "Station", "s1")
    private val quality = "https://example.com/quality"

    @Test
    fun `quality url is used when nothing else is set`() {
        val result = StreamSourceResolver.resolve(null, null, quality, defaultTitle = "SIR")

        assertThat(result).isEqualTo(StreamSource(quality, "SIR"))
    }

    @Test
    fun `selected station overrides the quality url`() {
        assertThat(StreamSourceResolver.resolve(null, station, quality)).isEqualTo(station)
    }

    @Test
    fun `debug override beats the selected station`() {
        val result = StreamSourceResolver.resolve("https://example.com/debug", station, quality)

        assertThat(result.url).isEqualTo("https://example.com/debug")
        assertThat(result.stationId).isNull()
    }

    @Test
    fun `blank overrides are ignored`() {
        assertThat(StreamSourceResolver.resolve("   ", station, quality)).isEqualTo(station)
    }

    @Test
    fun `station without a url falls through to quality`() {
        val broken = StreamSource(url = "", title = "Broken")

        assertThat(StreamSourceResolver.resolve(null, broken, quality).url).isEqualTo(quality)
    }
}
