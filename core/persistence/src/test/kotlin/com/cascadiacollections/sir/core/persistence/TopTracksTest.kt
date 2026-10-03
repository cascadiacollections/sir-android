package com.cascadiacollections.sir.core.persistence

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopTracksTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 24L * 60 * 60 * 1000

    // 2026-03-31T12:00:00Z — a month back lands on Feb 28, not "30 days ago" (Mar 1).
    private val now = 1_774_958_400_000L

    private fun heard(title: String, artist: String? = "Artist", at: Long = now) =
        HeardTrack(title = title, artist = artist, stationId = "s", stationName = "S", timestampMillis = at)

    private fun rank(history: List<HeardTrack>, timeframe: TopTracksTimeframe = TopTracksTimeframe.ALL_TIME) =
        TopTracks.rank(history, timeframe, nowMillis = now, timeZone = utc)

    @Test
    fun `ranks by play count and drops single plays`() {
        val history = listOf(heard("A"), heard("B"), heard("A"), heard("C"), heard("B"), heard("A"))
        val top = rank(history)
        assertEquals(listOf("A" to 3, "B" to 2), top.map { it.title to it.playCount })
    }

    @Test
    fun `ties break by most recently heard`() {
        val history = listOf(heard("A", at = now - 10), heard("A", at = now - 9), heard("B", at = now - 5), heard("B", at = now - 8))
        assertEquals(listOf("B", "A"), rank(history).map { it.title })
    }

    @Test
    fun `matches case-insensitively and shows the newest spelling`() {
        val top = rank(listOf(heard("song", artist = "band", at = now - 2), heard("Song", artist = "Band", at = now - 1)))
        assertEquals(1, top.size)
        assertEquals("Song", top.single().title)
        assertEquals("Band", top.single().artist)
        assertEquals(now - 1, top.single().lastHeardMillis)
    }

    @Test
    fun `tracks without an artist are not counted`() {
        assertTrue(rank(listOf(heard("A", artist = null), heard("A", artist = null))).isEmpty())
    }

    @Test
    fun `week only counts the last seven days`() {
        val history = listOf(heard("A", at = now - 8 * day), heard("A", at = now - 1 * day), heard("A", at = now))
        assertEquals(2, rank(history, TopTracksTimeframe.WEEK).single().playCount)
        assertEquals(3, rank(history, TopTracksTimeframe.ALL_TIME).single().playCount)
    }

    @Test
    fun `month is a calendar month back`() {
        // Feb 28 12:00 is inside "a month back from Mar 31 12:00"; Feb 28 11:59 is not.
        val feb28Noon = now - 31 * day
        val history = listOf(heard("A", at = feb28Noon), heard("A", at = now), heard("B", at = feb28Noon - 60_000), heard("B", at = now))
        val top = rank(history, TopTracksTimeframe.MONTH)
        assertEquals(listOf("A"), top.map { it.title })
    }

    @Test
    fun `caps the ranking at twenty`() {
        val history = (0 until 30).flatMap { i -> listOf(heard("T$i"), heard("T$i")) }
        assertEquals(TopTracks.LIMIT, rank(history).size)
    }

    @Test
    fun `an empty history ranks nothing`() {
        assertTrue(rank(emptyList()).isEmpty())
    }
}
