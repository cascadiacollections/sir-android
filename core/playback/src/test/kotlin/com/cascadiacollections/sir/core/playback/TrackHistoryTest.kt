package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackHistoryTest {

    private fun entry(title: String, artist: String? = "Artist", timestamp: Long = 0L) =
        TrackHistoryEntry(title, artist, timestamp)

    @Test
    fun `recording a track adds it to the front`() {
        val result = TrackHistory.record(emptyList(), entry("Song A", timestamp = 1L))

        assertEquals(listOf(entry("Song A", timestamp = 1L)), result)
    }

    @Test
    fun `newer tracks are recorded newest first`() {
        var history = emptyList<TrackHistoryEntry>()
        history = TrackHistory.record(history, entry("Song A", timestamp = 1L))
        history = TrackHistory.record(history, entry("Song B", timestamp = 2L))

        assertEquals(listOf("Song B", "Song A"), history.map { it.title })
    }

    @Test
    fun `repeating the front entry is a no-op rather than a duplicate`() {
        var history = emptyList<TrackHistoryEntry>()
        history = TrackHistory.record(history, entry("Song A", artist = "X", timestamp = 1L))
        history = TrackHistory.record(history, entry("Song A", artist = "X", timestamp = 2L))

        assertEquals(1, history.size)
        assertEquals(1L, history.single().timestampMillis)
    }

    @Test
    fun `the same title with a different artist is recorded as a new entry`() {
        var history = emptyList<TrackHistoryEntry>()
        history = TrackHistory.record(history, entry("Song A", artist = "X"))
        history = TrackHistory.record(history, entry("Song A", artist = "Y"))

        assertEquals(2, history.size)
    }

    @Test
    fun `replaying an earlier (non-front) track is recorded again, not deduped`() {
        var history = emptyList<TrackHistoryEntry>()
        history = TrackHistory.record(history, entry("Song A"))
        history = TrackHistory.record(history, entry("Song B"))
        history = TrackHistory.record(history, entry("Song A"))

        assertEquals(listOf("Song A", "Song B", "Song A"), history.map { it.title })
    }

    @Test
    fun `history is capped at the limit, dropping the oldest`() {
        var history = emptyList<TrackHistoryEntry>()
        repeat(30) { index -> history = TrackHistory.record(history, entry("Song $index"), limit = 25) }

        assertEquals(25, history.size)
        assertEquals("Song 29", history.first().title)
        assertEquals("Song 5", history.last().title)
    }

    @Test
    fun `default limit matches the 25-entry history the feature asks for`() {
        assertEquals(25, TrackHistory.LIMIT)
    }

    @Test
    fun `non-positive limit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackHistory.record(emptyList(), entry("Song A"), limit = 0)
        }
    }
}
