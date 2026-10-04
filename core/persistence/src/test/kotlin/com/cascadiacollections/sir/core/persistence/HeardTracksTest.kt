package com.cascadiacollections.sir.core.persistence

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.Test

class HeardTracksTest {

    private fun track(
        title: String,
        artist: String? = "Artist",
        stationId: String? = "s1",
        stationName: String = "Station One",
        at: Long = 0L
    ) = HeardTrack(title, artist, stationId, stationName, at)

    @Test
    fun `new tracks go to the front`() {
        var history = emptyList<HeardTrack>()
        history = HeardTracks.record(history, track("A", at = 1))
        history = HeardTracks.record(history, track("B", at = 2))
        assertThat(history.map { it.title }).containsExactly("B", "A")
    }

    @Test
    fun `a consecutive repeat merges and moves the timestamp forward`() {
        var history = listOf(track("A", at = 100))
        history = HeardTracks.record(history, track("A", stationName = "Renamed", at = 200))
        assertThat(history).hasSize(1)
        assertThat(history.single().timestampMillis).isEqualTo(200L)
        assertThat(history.single().stationName).isEqualTo("Renamed")
    }

    @Test
    fun `an out-of-order repeat never regresses the timestamp`() {
        val history = HeardTracks.record(listOf(track("A", at = 200)), track("A", at = 100))
        assertThat(history.single().timestampMillis).isEqualTo(200L)
    }

    @Test
    fun `the same track on another station or with another artist is a new entry`() {
        var history = listOf(track("A", at = 1))
        history = HeardTracks.record(history, track("A", stationId = "s2", at = 2))
        history = HeardTracks.record(history, track("A", artist = null, stationId = "s2", at = 3))
        assertThat(history).hasSize(3)
    }

    @Test
    fun `a non-consecutive repeat is a second play`() {
        var history = emptyList<HeardTrack>()
        listOf(
            "A",
            "B",
            "A"
        ).forEachIndexed { i, t ->
            history = HeardTracks.record(history, track(t, at = i.toLong()))
        }
        assertThat(history.map { it.title }).containsExactly("A", "B", "A")
    }

    @Test
    fun `history is capped with the oldest dropped`() {
        var history = emptyList<HeardTrack>()
        repeat(1005) { i -> history = HeardTracks.record(history, track("T$i", at = i.toLong())) }
        assertThat(history).hasSize(HeardTracks.LIMIT)
        assertThat(history.first().title).isEqualTo("T1004")
        assertThat(history.last().title).isEqualTo("T5")
    }

    @Test
    fun `merging at the cap keeps the cap`() {
        val full = (0 until 3).map { track("T$it", at = it.toLong()) }
        val result = HeardTracks.record(full, track("T0", at = 9), limit = 3)
        assertThat(result).hasSize(3)
    }

    @Test
    fun `a track with neither title nor artist is not recorded`() {
        assertThat(HeardTracks.record(emptyList(), track("  ", artist = null))).isEmpty()
        assertThat(HeardTracks.record(emptyList(), track("", artist = " "))).isEmpty()
    }

    @Test
    fun `an artist alone is recorded, as in ShoutKit`() {
        val recorded = HeardTracks.record(emptyList(), track("", artist = "Band")).single()
        assertThat(recorded.copyText).isEqualTo("Band")
    }

    @Test
    fun `artwork attaches to the matching current hearing`() {
        val history = HeardTracks.record(HeardTracks.record(emptyList(), track("A", at = 1)), track("B", at = 2))
        val updated = HeardTracks.attachArtwork(history, "B", "Artist", "s1", "https://art/b")
        assertThat(updated.map { it.artworkUrl }).containsExactly("https://art/b", null)
        assertThat(updated.map { it.timestampMillis }).isEqualTo(history.map { it.timestampMillis })
    }

    @Test
    fun `artwork arriving after a station switch adds no row`() {
        val history = HeardTracks.record(emptyList(), track("A", stationId = "old", at = 1))
        // The lookup for "A" finished after the listener switched to station "new".
        val updated = HeardTracks.attachArtwork(history, "A", "Artist", "new", "https://art/a")
        assertThat(updated).isEqualTo(history)
    }

    @Test
    fun `artwork for a track that is no longer current is dropped`() {
        val history = HeardTracks.record(HeardTracks.record(emptyList(), track("A", at = 1)), track("B", at = 2))
        assertThat(HeardTracks.attachArtwork(history, "A", "Artist", "s1", "https://art/a")).isEqualTo(history)
        assertThat(HeardTracks.attachArtwork(emptyList(), "A", "Artist", "s1", "https://art/a")).isEmpty()
    }

    @Test
    fun `a consecutive repeat adopts newly found artwork and keeps it`() {
        val first = HeardTracks.record(emptyList(), track("A", at = 1))
        val withArt = HeardTracks.record(first, track("A", at = 2).copy(artworkUrl = "https://art"))
        assertThat(withArt.map { it.artworkUrl }).containsExactly("https://art")
        val again = HeardTracks.record(withArt, track("A", at = 3))
        assertThat(again.map { it.artworkUrl }).containsExactly("https://art")
    }

    @Test
    fun `limit must be positive`() {
        assertFailure {
            HeardTracks.record(emptyList(), track("A"), limit = 0)
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `codec round-trips and tolerates garbage`() {
        val history =
            listOf(track("A", at = 5), track("B", artist = null, stationId = null, at = 4))
        assertThat(HeardTracks.decode(HeardTracks.encode(history))).isEqualTo(history)
        assertThat(HeardTracks.decode("{not json")).isEmpty()
        assertThat(HeardTracks.decode(null)).isEmpty()
    }

    @Test
    fun `copy text joins title and artist`() {
        assertThat(track("A").copyText).isEqualTo("A — Artist")
        assertThat(track("A", artist = null).copyText).isEqualTo("A")
    }
}
