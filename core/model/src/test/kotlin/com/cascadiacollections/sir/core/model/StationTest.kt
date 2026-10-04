package com.cascadiacollections.sir.core.model

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.util.Locale
import kotlinx.serialization.json.Json
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

        assertThat(station.id).isEqualTo("abc")
        assertThat(station.countryCode).isEqualTo("US")
        assertThat(station.tagList).containsExactly("jazz", "soul")
    }

    @Test
    fun `display label includes codec and bitrate when known`() {
        val station = Station(name = "Test FM", codec = "mp3", bitrate = 128)

        assertThat(station.displayLabel).isEqualTo("Test FM (MP3, 128kbps)")
    }

    @Test
    fun `display label is the plain name when codec is unknown`() {
        assertThat(Station(name = "Test FM").displayLabel).isEqualTo("Test FM")
    }

    @Test
    fun `station without url is not playable`() {
        assertThat(Station(name = "Broken").isPlayable).isFalse()
        assertThat(Station(name = "Ok", url = "https://example.com/s").isPlayable).isTrue()
    }

    @Test
    fun `tag list drops blanks and whitespace`() {
        assertThat(Station(tags = " a , ,b , ").tagList).containsExactly("a", "b")
    }

    @Test
    fun `display label omits bitrate when the directory did not report one`() {
        // radio-browser leaves bitrate at 0 for many entries; "0kbps" reads as broken.
        val station = Station(name = "Test FM", codec = "aac")

        assertThat(station.displayLabel).isEqualTo("Test FM (AAC)")
    }

    @Test
    fun `display label does not depend on the device locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            // A Turkish locale uppercases 'i' to the dotted 'İ', mangling the codec token.
            val station = Station(name = "Test FM", codec = "vorbis", bitrate = 64)

            assertThat(station.displayLabel).isEqualTo("Test FM (VORBIS, 64kbps)")
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `stream url prefers the resolved url`() {
        val station =
            Station(url = "http://example.com/listen.pls", urlResolved = "http://example.com:8000/stream")

        assertThat(station.streamUrl).isEqualTo("http://example.com:8000/stream")
    }

    @Test
    fun `stream url falls back to url when resolved url is blank`() {
        assertThat(Station(url = "https://example.com/s").streamUrl).isEqualTo("https://example.com/s")
        assertThat(Station(url = "https://example.com/s", urlResolved = "  ").streamUrl)
            .isEqualTo("https://example.com/s")
    }

    @Test
    fun `station with only a resolved url is playable`() {
        assertThat(Station(urlResolved = "https://example.com/s").isPlayable).isTrue()
    }

    @Test
    fun `decodes url_resolved and hls from the radio-browser payload`() {
        val payload = """
            {"stationuuid":"abc","name":"HLS FM","url":"https://example.com/live",
             "url_resolved":"https://cdn.example.com/live/master.m3u8","hls":1}
        """.trimIndent()

        val station = json.decodeFromString<Station>(payload)

        assertThat(station.urlResolved).isEqualTo("https://cdn.example.com/live/master.m3u8")
        assertThat(station.streamUrl).isEqualTo("https://cdn.example.com/live/master.m3u8")
        assertThat(station.isHls).isTrue()
    }

    @Test
    fun `decodes previously persisted json without url_resolved or hls`() {
        // The shape saved_stations had before url_resolved and hls were modelled.
        val persisted = """
            [{"stationuuid":"abc","name":"Old FM","url":"http://example.com/stream",
              "favicon":null,"bitrate":64,"codec":"MP3","countrycode":"US","tags":""}]
        """.trimIndent()

        val station = Json.decodeFromString<List<Station>>(persisted).single()

        assertThat(station.urlResolved).isEmpty()
        assertThat(station.hls).isEqualTo(0)
        assertThat(station.isHls).isFalse()
        assertThat(station.streamUrl).isEqualTo("http://example.com/stream")
        assertThat(station.isPlayable).isTrue()
    }

    @Test
    fun `round trips through json`() {
        val station = Station(id = "a", name = "A", url = "u", urlResolved = "r", hls = 1)

        assertThat(Json.decodeFromString<Station>(Json.encodeToString(Station.serializer(), station)))
            .isEqualTo(station)
    }

    @Test
    fun `changing the url drops the directory's resolved url and hls flag`() {
        val station =
            Station(url = "https://a.example/s", urlResolved = "https://a.example/r.m3u8", hls = 1)

        val edited = station.withUrl("http://b.example/stream")

        assertThat(edited.streamUrl).isEqualTo("http://b.example/stream")
        assertThat(edited.isHls).isFalse()
        assertThat(station.withUrl("https://a.example/s")).isEqualTo(station)
    }
}
