package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StationCollectionsTest {

    private fun station(id: String, name: String = id, url: String = "https://example.com/$id") =
        Station(id = id, name = name, url = url)

    @Test
    fun `adding a new favorite appends to the end`() {
        val result = StationCollections.addFavorite(listOf(station("a")), station("b"))

        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `re-adding a favorite refreshes metadata without reordering`() {
        val current = listOf(station("a"), station("b"), station("c"))
        val refreshed = station("b").copy(bitrate = 320)

        val result = StationCollections.addFavorite(current, refreshed)

        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals(320, result[1].bitrate)
    }

    @Test
    fun `removing a favorite leaves the rest in order`() {
        val current = listOf(station("a"), station("b"), station("c"))

        assertEquals(listOf("a", "c"), StationCollections.removeFavorite(current, "b").map { it.id })
    }

    @Test
    fun `removing an unknown favorite is a no-op`() {
        val current = listOf(station("a"))

        assertEquals(current, StationCollections.removeFavorite(current, "zzz"))
    }

    @Test
    fun `recents are newest first`() {
        var recents = emptyList<Station>()
        recents = StationCollections.recordRecent(recents, station("a"))
        recents = StationCollections.recordRecent(recents, station("b"))

        assertEquals(listOf("b", "a"), recents.map { it.id })
    }

    @Test
    fun `replaying a station moves it to the front instead of duplicating`() {
        val current = listOf(station("a"), station("b"), station("c"))

        val result = StationCollections.recordRecent(current, station("c"))

        assertEquals(listOf("c", "a", "b"), result.map { it.id })
    }

    @Test
    fun `recents are capped at the limit dropping the oldest`() {
        var recents = emptyList<Station>()
        repeat(5) { index -> recents = StationCollections.recordRecent(recents, station("s$index"), limit = 3) }

        assertEquals(listOf("s4", "s3", "s2"), recents.map { it.id })
    }

    @Test
    fun `unplayable stations are never recorded`() {
        val current = listOf(station("a"))

        assertEquals(current, StationCollections.recordRecent(current, Station(id = "b", name = "No URL")))
    }

    @Test
    fun `non-positive recents limit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            StationCollections.recordRecent(emptyList(), station("a"), limit = 0)
        }
    }
}

class StationCodecTest {

    private val station = Station(id = "a", name = "A", url = "https://example.com/a")

    @Test
    fun `round trip preserves stations`() {
        val encoded = StationCodec.encode(listOf(station))

        assertEquals(listOf(station), StationCodec.decode(encoded))
    }

    @Test
    fun `corrupt payload decodes to empty rather than throwing`() {
        assertEquals(emptyList<Station>(), StationCodec.decode("{not json"))
    }

    @Test
    fun `null and blank payloads decode to empty`() {
        assertEquals(emptyList<Station>(), StationCodec.decode(null))
        assertEquals(emptyList<Station>(), StationCodec.decode("   "))
    }

    @Test
    fun `legacy payloads with unknown fields still decode`() {
        val legacy = """[{"stationuuid":"a","name":"A","url":"https://example.com/a","clickcount":42}]"""

        assertEquals(listOf(station), StationCodec.decode(legacy))
    }
}
