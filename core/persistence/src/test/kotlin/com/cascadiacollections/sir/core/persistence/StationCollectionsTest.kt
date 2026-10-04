package com.cascadiacollections.sir.core.persistence

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.cascadiacollections.sir.core.model.Station
import org.junit.Test

class StationCollectionsTest {

    private fun station(id: String, name: String = id, url: String = "https://example.com/$id") =
        Station(id = id, name = name, url = url)

    @Test
    fun `recents keep ShoutKit's twenty-five and remove one by id`() {
        val recents = (1..30).fold(
            emptyList<Station>()
        ) { acc, i -> StationCollections.recordRecent(acc, station("s$i")) }
        assertThat(recents).hasSize(25)
        assertThat(recents.first().id).isEqualTo("s30")

        val removed = StationCollections.removeRecent(recents, "s29")
        assertThat(removed.take(2).map { it.id }).containsExactly("s30", "s28")
        assertThat(removed).hasSize(24)
    }

    @Test
    fun `adding a new favorite appends to the end`() {
        val result = StationCollections.addFavorite(listOf(station("a")), station("b"))

        assertThat(result.map { it.id }).containsExactly("a", "b")
    }

    @Test
    fun `re-adding a favorite refreshes metadata without reordering`() {
        val current = listOf(station("a"), station("b"), station("c"))
        val refreshed = station("b").copy(bitrate = 320)

        val result = StationCollections.addFavorite(current, refreshed)

        assertThat(result.map { it.id }).containsExactly("a", "b", "c")
        assertThat(result[1].bitrate).isEqualTo(320)
    }

    @Test
    fun `removing a favorite leaves the rest in order`() {
        val current = listOf(station("a"), station("b"), station("c"))

        assertThat(StationCollections.removeFavorite(current, "b").map { it.id })
            .containsExactly("a", "c")
    }

    @Test
    fun `removing an unknown favorite is a no-op`() {
        val current = listOf(station("a"))

        assertThat(StationCollections.removeFavorite(current, "zzz")).isEqualTo(current)
    }

    @Test
    fun `findByName prefers an exact case-insensitive match over a substring match`() {
        val current = listOf(station("a", name = "Classical NPR"), station("b", name = "NPR"))

        assertThat(StationCollections.findByName(current, "npr")?.id).isEqualTo("b")
    }

    @Test
    fun `findByName falls back to a substring match`() {
        val current = listOf(station("a", name = "NPR News"))

        assertThat(StationCollections.findByName(current, "npr")?.id).isEqualTo("a")
    }

    @Test
    fun `findByName returns null when nothing matches`() {
        val current = listOf(station("a", name = "Jazz FM"))

        assertThat(StationCollections.findByName(current, "rock")).isNull()
    }

    @Test
    fun `findByName trims leading and trailing whitespace before matching`() {
        val current = listOf(station("a", name = "NPR"))

        assertThat(StationCollections.findByName(current, "  NPR  ")?.id).isEqualTo("a")
    }

    @Test
    fun `findByName returns null for a blank or whitespace-only query`() {
        val current = listOf(station("a", name = "NPR"))

        assertThat(StationCollections.findByName(current, "")).isNull()
        assertThat(StationCollections.findByName(current, "   ")).isNull()
    }

    @Test
    fun `recents are newest first`() {
        var recents = emptyList<Station>()
        recents = StationCollections.recordRecent(recents, station("a"))
        recents = StationCollections.recordRecent(recents, station("b"))

        assertThat(recents.map { it.id }).containsExactly("b", "a")
    }

    @Test
    fun `replaying a station moves it to the front instead of duplicating`() {
        val current = listOf(station("a"), station("b"), station("c"))

        val result = StationCollections.recordRecent(current, station("c"))

        assertThat(result.map { it.id }).containsExactly("c", "a", "b")
    }

    @Test
    fun `recents are capped at the limit dropping the oldest`() {
        var recents = emptyList<Station>()
        repeat(5) { index ->
            recents = StationCollections.recordRecent(recents, station("s$index"), limit = 3)
        }

        assertThat(recents.map { it.id }).containsExactly("s4", "s3", "s2")
    }

    @Test
    fun `unplayable stations are never recorded`() {
        val current = listOf(station("a"))

        assertThat(StationCollections.recordRecent(current, Station(id = "b", name = "No URL"))).isEqualTo(current)
    }

    @Test
    fun `non-positive recents limit is rejected`() {
        assertFailure {
            StationCollections.recordRecent(emptyList(), station("a"), limit = 0)
        }.isInstanceOf<IllegalArgumentException>()
    }
}

class StationCodecTest {

    private val station = Station(id = "a", name = "A", url = "https://example.com/a")

    @Test
    fun `round trip preserves stations`() {
        val encoded = StationCodec.encode(listOf(station))

        assertThat(StationCodec.decode(encoded)).containsExactly(station)
    }

    @Test
    fun `corrupt payload decodes to empty rather than throwing`() {
        assertThat(StationCodec.decode("{not json")).isEmpty()
    }

    @Test
    fun `null and blank payloads decode to empty`() {
        assertThat(StationCodec.decode(null)).isEmpty()
        assertThat(StationCodec.decode("   ")).isEmpty()
    }

    @Test
    fun `legacy payloads with unknown fields still decode`() {
        val legacy = """[{"stationuuid":"a","name":"A","url":"https://example.com/a","clickcount":42}]"""

        assertThat(StationCodec.decode(legacy)).containsExactly(station)
    }
}
