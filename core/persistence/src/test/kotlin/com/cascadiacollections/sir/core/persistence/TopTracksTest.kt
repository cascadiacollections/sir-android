package com.cascadiacollections.sir.core.persistence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import java.util.TimeZone
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
    fun `ranks by play count, including tracks heard once as ShoutKit does`() {
        val history = listOf(heard("A"), heard("B"), heard("A"), heard("C"), heard("B"), heard("A"))
        val top = rank(history)
        assertThat(top.map { it.title to it.playCount })
            .containsExactly("A" to 3, "B" to 2, "C" to 1)
    }

    @Test
    fun `artwork comes from the newest hearing that has some`() {
        val history = listOf(
            heard("A", at = now - 3).copy(artworkUrl = "https://art/old"),
            heard("A", at = now - 2).copy(artworkUrl = "https://art/new"),
            heard("A", at = now - 1)
        )
        assertThat(rank(history).single().artworkUrl).isEqualTo("https://art/new")
    }

    @Test
    fun `ties break by most recently heard`() {
        val history = listOf(
            heard("A", at = now - 10),
            heard("A", at = now - 9),
            heard("B", at = now - 5),
            heard("B", at = now - 8)
        )
        assertThat(rank(history).map { it.title }).containsExactly("B", "A")
    }

    @Test
    fun `matches case-insensitively and shows the newest spelling`() {
        val top = rank(
            listOf(heard("song", artist = "band", at = now - 2), heard("Song", artist = "Band", at = now - 1))
        )
        assertThat(top).hasSize(1)
        assertThat(top.single().title).isEqualTo("Song")
        assertThat(top.single().artist).isEqualTo("Band")
        assertThat(top.single().lastHeardMillis).isEqualTo(now - 1)
    }

    @Test
    fun `tracks without an artist are not counted`() {
        assertThat(rank(listOf(heard("A", artist = null), heard("A", artist = null)))).isEmpty()
    }

    @Test
    fun `week only counts the last seven days`() {
        val history =
            listOf(heard("A", at = now - 8 * day), heard("A", at = now - 1 * day), heard("A", at = now))
        assertThat(rank(history, TopTracksTimeframe.WEEK).single().playCount).isEqualTo(2)
        assertThat(rank(history, TopTracksTimeframe.ALL_TIME).single().playCount).isEqualTo(3)
    }

    @Test
    fun `month is a calendar month back`() {
        // Feb 28 12:00 is inside "a month back from Mar 31 12:00"; Feb 28 11:59 is not.
        val feb28Noon = now - 31 * day
        val history = listOf(
            heard("A", at = feb28Noon),
            heard("A", at = now),
            heard("B", at = feb28Noon - 60_000),
            heard("B", at = now)
        )
        val top = rank(history, TopTracksTimeframe.MONTH)
        assertThat(top.map { it.title to it.playCount }).containsExactly("A" to 2, "B" to 1)
    }

    @Test
    fun `caps the ranking at twenty`() {
        val history = (0 until 30).flatMap { i -> listOf(heard("T$i"), heard("T$i")) }
        assertThat(rank(history)).hasSize(TopTracks.LIMIT)
    }

    @Test
    fun `an empty history ranks nothing`() {
        assertThat(rank(emptyList())).isEmpty()
    }
}
