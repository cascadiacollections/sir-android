package com.cascadiacollections.sir.core.model

import java.util.Locale
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes radio-browser payload field names`() {
        val payload = """
            [{"stationuuid":"abc","name":"Test FM","url":"https://example.com/s",
              "bitrate":128,"codec":"MP3","countrycode":"US","tags":"jazz, soul",
              "unknownField":"ignored"}]
        """.trimIndent()

        val station = json.decodeFromString<List<Station>>(payload).single()

        assertEquals("abc", station.id)
        assertEquals("US", station.countryCode)
        assertEquals(listOf("jazz", "soul"), station.tagList)
    }

    @Test
    fun `display label includes codec and bitrate when known`() {
        val station = Station(name = "Test FM", codec = "mp3", bitrate = 128)

        assertEquals("Test FM (MP3, 128kbps)", station.displayLabel)
    }

    @Test
    fun `display label is the plain name when codec is unknown`() {
        assertEquals("Test FM", Station(name = "Test FM").displayLabel)
    }

    @Test
    fun `station without url is not playable`() {
        assertFalse(Station(name = "Broken").isPlayable)
        assertTrue(Station(name = "Ok", url = "https://example.com/s").isPlayable)
    }

    @Test
    fun `tag list drops blanks and whitespace`() {
        assertEquals(listOf("a", "b"), Station(tags = " a , ,b , ").tagList)
    }

    @Test
    fun `display label omits bitrate when the directory did not report one`() {
        // radio-browser leaves bitrate at 0 for many entries; "0kbps" reads as broken.
        val station = Station(name = "Test FM", codec = "aac")

        assertEquals("Test FM (AAC)", station.displayLabel)
    }

    @Test
    fun `display label does not depend on the device locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            // A Turkish locale uppercases 'i' to the dotted 'İ', mangling the codec token.
            val station = Station(name = "Test FM", codec = "vorbis", bitrate = 64)

            assertEquals("Test FM (VORBIS, 64kbps)", station.displayLabel)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `stream url prefers the resolved url`() {
        val station =
            Station(url = "http://example.com/listen.pls", urlResolved = "http://example.com:8000/stream")

        assertEquals("http://example.com:8000/stream", station.streamUrl)
    }

    @Test
    fun `stream url falls back to url when resolved url is blank`() {
        assertEquals("https://example.com/s", Station(url = "https://example.com/s").streamUrl)
        assertEquals("https://example.com/s", Station(url = "https://example.com/s", urlResolved = "  ").streamUrl)
    }

    @Test
    fun `station with only a resolved url is playable`() {
        assertTrue(Station(urlResolved = "https://example.com/s").isPlayable)
    }

    @Test
    fun `decodes url_resolved and hls from the radio-browser payload`() {
        val payload = """
            {"stationuuid":"abc","name":"HLS FM","url":"https://example.com/live",
             "url_resolved":"https://cdn.example.com/live/master.m3u8","hls":1}
        """.trimIndent()

        val station = json.decodeFromString<Station>(payload)

        assertEquals("https://cdn.example.com/live/master.m3u8", station.urlResolved)
        assertEquals("https://cdn.example.com/live/master.m3u8", station.streamUrl)
        assertTrue(station.isHls)
    }

    @Test
    fun `decodes previously persisted json without url_resolved or hls`() {
        // The shape saved_stations had before url_resolved and hls were modelled.
        val persisted = """
            [{"stationuuid":"abc","name":"Old FM","url":"http://example.com/stream",
              "favicon":null,"bitrate":64,"codec":"MP3","countrycode":"US","tags":""}]
        """.trimIndent()

        val station = Json.decodeFromString<List<Station>>(persisted).single()

        assertEquals("", station.urlResolved)
        assertEquals(0, station.hls)
        assertFalse(station.isHls)
        assertEquals("http://example.com/stream", station.streamUrl)
        assertTrue(station.isPlayable)
    }

    @Test
    fun `round trips through json`() {
        val station = Station(id = "a", name = "A", url = "u", urlResolved = "r", hls = 1)

        assertEquals(station, Json.decodeFromString<Station>(Json.encodeToString(Station.serializer(), station)))
    }

    @Test
    fun `changing the url drops the directory's resolved url and hls flag`() {
        val station =
            Station(url = "https://a.example/s", urlResolved = "https://a.example/r.m3u8", hls = 1)

        val edited = station.withUrl("http://b.example/stream")

        assertEquals("http://b.example/stream", edited.streamUrl)
        assertFalse(edited.isHls)
        assertEquals(station, station.withUrl("https://a.example/s"))
    }
}
