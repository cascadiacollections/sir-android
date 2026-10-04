package com.cascadiacollections.sir.core.persistence

import java.util.Calendar
import java.util.TimeZone

/** How far back a Top Tracks report looks — ShoutKit's `TopTracksTimeframe`. */
enum class TopTracksTimeframe {
    WEEK,
    MONTH,
    ALL_TIME;

    /**
     * The earliest timestamp included, or null for no lower bound. A month is a calendar
     * month back from [nowMillis] in [timeZone], not a fixed 30 days.
     */
    fun sinceMillis(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long? =
        when (this) {
            WEEK -> nowMillis - 7L * 24 * 60 * 60 * 1000
            MONTH -> Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMillis
                add(Calendar.MONTH, -1)
            }.timeInMillis
            ALL_TIME -> null
        }
}

/** One ranked row: a (title, artist) pair collapsed across every hearing of it. */
data class TopTrack(
    val title: String,
    val artist: String,
    val playCount: Int,
    val lastHeardMillis: Long,
    /** Cover art from the most recent hearing that had some. */
    val artworkUrl: String? = null,
)

/**
 * Aggregates the Recently Heard history into a "most played" ranking — a port of
 * ShoutKit's `TopTracksAggregator`.
 */
object TopTracks {

    const val LIMIT: Int = 20

    /** As in ShoutKit, every counted track is ranked, including ones heard once. */
    const val MIN_PLAYS: Int = 1

    /**
     * Ranks [history] (any order) by play count, ties broken by most recently heard.
     *
     * Tracks match case-insensitively on title and artist. Both are required, as in
     * ShoutKit: one-sided ICY metadata can't be matched reliably across plays. The most
     * recent hearing supplies the displayed spelling.
     *
     * @param nowMillis injectable clock, so the timeframes are testable.
     */
    fun rank(
        history: List<HeardTrack>,
        timeframe: TopTracksTimeframe,
        nowMillis: Long,
        limit: Int = LIMIT,
        minPlays: Int = MIN_PLAYS,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<TopTrack> {
        val since = timeframe.sinceMillis(nowMillis, timeZone)
        val buckets = LinkedHashMap<String, TopTrack>()
        for (track in history) {
            val title = track.title.takeIf { it.isNotBlank() } ?: continue
            val artist = track.artist?.takeIf { it.isNotBlank() } ?: continue
            if (since != null && track.timestampMillis < since) continue

            // A unit separator rather than a printable delimiter: titles and artists can
            // legitimately contain "|" or " - ", which would let distinct tracks collide.
            val key = title.lowercase() + '\u001F' + artist.lowercase()
            val existing = buckets[key]
            buckets[key] = if (existing == null) {
                TopTrack(title, artist, playCount = 1, lastHeardMillis = track.timestampMillis, artworkUrl = track.artworkUrl)
            } else {
                val isNewer = track.timestampMillis > existing.lastHeardMillis
                TopTrack(
                    title = if (isNewer) title else existing.title,
                    artist = if (isNewer) artist else existing.artist,
                    playCount = existing.playCount + 1,
                    lastHeardMillis = maxOf(existing.lastHeardMillis, track.timestampMillis),
                    artworkUrl = if (isNewer) track.artworkUrl ?: existing.artworkUrl
                    else existing.artworkUrl ?: track.artworkUrl,
                )
            }
        }
        return buckets.values
            .filter { it.playCount >= minPlays }
            .sortedWith(
                compareByDescending<TopTrack> { it.playCount }.thenByDescending { it.lastHeardMillis }
            )
            .take(limit)
    }
}
