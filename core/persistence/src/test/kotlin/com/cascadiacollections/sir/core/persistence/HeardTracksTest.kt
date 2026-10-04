package com.cascadiacollections.sir.core.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HeardTracksTest {

    private fun track(
        title: String,
        artist: String? = "Artist",
        stationId: String? = "s1",
        stationName: String = "Station One",
        at: Long = 0L,
    ) = HeardTrack(title, artist, stationId, stationName, at)

    @Test
    fun `new tracks go to the front`() {
        var history = emptyList<HeardTrack>()
        history = HeardTracks.record(history, track("A", at = 1))
        history = HeardTracks.record(history, track("B", at = 2))
        assertEquals(listOf("B", "A"), history.map { it.title })
    }

    @Test
    fun `a consecutive repeat merges and moves the timestamp forward`() {
        var history = listOf(track("A", at = 100))
        history = HeardTracks.record(history, track("A", stationName = "Renamed", at = 200))
        assertEquals(1, history.size)
        assertEquals(200L, history.single().timestampMillis)
        assertEquals("Renamed", history.single().stationName)
    }

    @Test
    fun `an out-of-order repeat never regresses the timestamp`() {
        val history = HeardTracks.record(listOf(track("A", at = 200)), track("A", at = 100))
        assertEquals(200L, history.single().timestampMillis)
    }

    @Test
    fun `the same track on another station or with another artist is a new entry`() {
        var history = listOf(track("A", at = 1))
        history = HeardTracks.record(history, track("A", stationId = "s2", at = 2))
        history = HeardTracks.record(history, track("A", artist = null, stationId = "s2", at = 3))
        assertEquals(3, history.size)
    }

    @Test
    fun `a non-consecutive repeat is a second play`() {
        var history = emptyList<HeardTrack>()
        listOf("A", "B", "A").forEachIndexed { i, t -> history = HeardTracks.record(history, track(t, at = i.toLong())) }
        assertEquals(listOf("A", "B", "A"), history.map { it.title })
    }

    @Test
    fun `history is capped with the oldest dropped`() {
        var history = emptyList<HeardTrack>()
        repeat(1005) { i -> history = HeardTracks.record(history, track("T$i", at = i.toLong())) }
        assertEquals(HeardTracks.LIMIT, history.size)
        assertEquals("T1004", history.first().title)
        assertEquals("T5", history.last().title)
    }

    @Test
    fun `merging at the cap keeps the cap`() {
        val full = (0 until 3).map { track("T$it", at = it.toLong()) }
        val result = HeardTracks.record(full, track("T0", at = 9), limit = 3)
        assertEquals(3, result.size)
    }

    @Test
    fun `a track with neither title nor artist is not recorded`() {
        assertTrue(HeardTracks.record(emptyList(), track("  ", artist = null)).isEmpty())
        assertTrue(HeardTracks.record(emptyList(), track("", artist = " ")).isEmpty())
    }

    @Test
    fun `an artist alone is recorded, as in ShoutKit`() {
        val recorded = HeardTracks.record(emptyList(), track("", artist = "Band")).single()
        assertEquals("Band", recorded.copyText)
    }

    @Test
    fun `a consecutive repeat adopts newly found artwork and keeps it`() {
        val first = HeardTracks.record(emptyList(), track("A", at = 1))
        val withArt = HeardTracks.record(first, track("A", at = 2).copy(artworkUrl = "https://art"))
        assertEquals(listOf("https://art"), withArt.map { it.artworkUrl })
        val again = HeardTracks.record(withArt, track("A", at = 3))
        assertEquals(listOf("https://art"), again.map { it.artworkUrl })
    }

    @Test
    fun `limit must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            HeardTracks.record(emptyList(), track("A"), limit = 0)
        }
    }

    @Test
    fun `codec round-trips and tolerates garbage`() {
        val history = listOf(track("A", at = 5), track("B", artist = null, stationId = null, at = 4))
        assertEquals(history, HeardTracks.decode(HeardTracks.encode(history)))
        assertEquals(emptyList<HeardTrack>(), HeardTracks.decode("{not json"))
        assertEquals(emptyList<HeardTrack>(), HeardTracks.decode(null))
    }

    @Test
    fun `copy text joins title and artist`() {
        assertEquals("A — Artist", track("A").copyText)
        assertEquals("A", track("A", artist = null).copyText)
    }
}
