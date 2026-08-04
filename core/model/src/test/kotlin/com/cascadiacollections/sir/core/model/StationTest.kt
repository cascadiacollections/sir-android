package com.cascadiacollections.sir.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
}
