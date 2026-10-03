package com.cascadiacollections.sir.core.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One track heard on a stream, resolved from ICY metadata by the playback service.
 *
 * Mirrors ShoutKit's `RecentlyHeardTrack`: the station is recorded by id *and* name so a
 * row still reads correctly after the station is unsaved, renamed or disappears from the
 * directory. [stationId] is null for the app's own stream, which has no directory id.
 */
@Serializable
data class HeardTrack(
    val title: String,
    val artist: String? = null,
    val stationId: String? = null,
    val stationName: String = "",
    val timestampMillis: Long = 0L,
) {
    /** "Title — Artist", or just the title when the artist is unknown. */
    val copyText: String get() = listOfNotNull(title, artist).joinToString(" — ")
}

/**
 * Rules for the persisted "Recently Heard" history, newest first.
 *
 * Pure list transformations, like [StationCollections], so the merge/cap behaviour is
 * testable without DataStore.
 */
object HeardTracks {

    /** How many tracks are retained — ShoutKit's `recentlyHeardLimit`. */
    const val LIMIT: Int = 1000

    /** How many tracks the in-player history sheet shows. */
    const val SHEET_LIMIT: Int = 25

    /**
     * Records [track] as the most recently heard, capped at [limit].
     *
     * A consecutive repeat — the same title and artist on the same station as the entry
     * already at the front — is merged rather than duplicated: ICY servers re-send an
     * unchanged `StreamTitle` on unrelated updates, and a service restart re-announces the
     * current track. As in ShoutKit, the merged row's timestamp moves forward (never back,
     * since callbacks can arrive out of order) so it reflects the latest hearing, and its
     * station name is refreshed.
     *
     * The same track heard again after something else played is a new entry: that is a
     * genuine second play, and it is what Top Tracks counts.
     */
    fun record(
        current: List<HeardTrack>,
        track: HeardTrack,
        limit: Int = LIMIT,
    ): List<HeardTrack> {
        require(limit > 0) { "limit must be positive" }
        if (track.title.isBlank()) return current
        val front = current.firstOrNull()
        if (front != null &&
            front.stationId == track.stationId &&
            front.title == track.title &&
            front.artist == track.artist
        ) {
            val merged = front.copy(
                stationName = track.stationName,
                timestampMillis = maxOf(front.timestampMillis, track.timestampMillis),
            )
            return listOf(merged) + current.drop(1).take(limit - 1)
        }
        return (listOf(track) + current).take(limit)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Decodes the stored history. Total, like [StationCodec]: an unreadable blob decodes
     * as empty rather than taking the Library down with it.
     */
    fun decode(raw: String?): List<HeardTrack> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<HeardTrack>>(raw) }.getOrDefault(emptyList())
    }

    fun encode(tracks: List<HeardTrack>): String = json.encodeToString(tracks)
}
