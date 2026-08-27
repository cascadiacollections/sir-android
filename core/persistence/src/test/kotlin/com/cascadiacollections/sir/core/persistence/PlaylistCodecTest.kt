package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCodecTest {

    @Test
    fun `parses a well-formed M3U playlist`() {
        val text = """
            #EXTM3U
            #EXTINF:-1,Station A
            https://example.com/a
            #EXTINF:-1,Station B
            https://example.com/b
        """.trimIndent()

        val stations = PlaylistCodec.parseM3u(text)

        assertEquals(listOf("Station A", "Station B"), stations.map { it.name })
        assertEquals(listOf("https://example.com/a", "https://example.com/b"), stations.map { it.url })
    }

    @Test
    fun `M3U entries without EXTINF fall back to the URL's last path segment`() {
        val text = "#EXTM3U\nhttps://example.com/stream-a"

        val stations = PlaylistCodec.parseM3u(text)

        assertEquals("stream-a", stations.single().name)
    }

    @Test
    fun `M3U parsing ignores blank lines and unrelated comments`() {
        val text = """
            #EXTM3U

            #PLAYLIST:My Playlist
            #EXTINF:-1,Station A
            https://example.com/a
        """.trimIndent()

        assertEquals(1, PlaylistCodec.parseM3u(text).size)
    }

    @Test
    fun `parses a well-formed PLS playlist regardless of entry order`() {
        val text = """
            [playlist]
            NumberOfEntries=2
            Title1=Station A
            File1=https://example.com/a
            File2=https://example.com/b
            Title2=Station B
            Version=2
        """.trimIndent()

        val stations = PlaylistCodec.parsePls(text)

        assertEquals(listOf("Station A", "Station B"), stations.map { it.name })
        assertEquals(listOf("https://example.com/a", "https://example.com/b"), stations.map { it.url })
    }

    @Test
    fun `PLS entries without a title fall back to the URL's last path segment`() {
        val text = "[playlist]\nFile1=https://example.com/stream-a\n"

        assertEquals("stream-a", PlaylistCodec.parsePls(text).single().name)
    }

    @Test
    fun `imported stations get a stable id derived from the URL`() {
        val first = PlaylistCodec.parseM3u("https://example.com/a").single()
        val second = PlaylistCodec.parseM3u("#EXTINF:-1,Renamed\nhttps://example.com/a").single()

        assertEquals(first.id, second.id)
        assertTrue(first.id.contains("https://example.com/a"))
    }

    @Test
    fun `blank input parses to no stations`() {
        assertEquals(emptyList<Station>(), PlaylistCodec.parseM3u(""))
        assertEquals(emptyList<Station>(), PlaylistCodec.parsePls(""))
    }

    @Test
    fun `toM3u round trips through parseM3u`() {
        val stations = listOf(
            Station(id = "a", name = "Station A", url = "https://example.com/a"),
            Station(id = "b", name = "Station B", url = "https://example.com/b")
        )

        val roundTripped = PlaylistCodec.parseM3u(PlaylistCodec.toM3u(stations))

        assertEquals(stations.map { it.name }, roundTripped.map { it.name })
        assertEquals(stations.map { it.url }, roundTripped.map { it.url })
    }
}
