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
    /** Blank when the stream named only the artist; shown as "Unknown Track", as in ShoutKit. */
    val title: String = "",
    val artist: String? = null,
    val stationId: String? = null,
    val stationName: String = "",
    val timestampMillis: Long = 0L,
    /** Cover art found for the track (iTunes Search), when the lookup is enabled and found some. */
    val artworkUrl: String? = null,
) {
    /** "Title — Artist", or whichever of the two is known. */
    val copyText: String
        get() = listOfNotNull(title.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
            .joinToString(" — ")
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
     *
     * Like ShoutKit, a track needs a title *or* an artist, and a merge adopts newly found
     * artwork — cover art is looked up after the track starts, so it arrives as a repeat.
     */
    fun record(
        current: List<HeardTrack>,
        track: HeardTrack,
        limit: Int = LIMIT,
    ): List<HeardTrack> {
        require(limit > 0) { "limit must be positive" }
        if (track.title.isBlank() && track.artist.isNullOrBlank()) return current
        val front = current.firstOrNull()
        if (front != null &&
            front.stationId == track.stationId &&
            front.title == track.title &&
            front.artist == track.artist
        ) {
            val merged = front.copy(
                stationName = track.stationName,
                timestampMillis = maxOf(front.timestampMillis, track.timestampMillis),
                artworkUrl = track.artworkUrl ?: front.artworkUrl,
            )
            return listOf(merged) + current.drop(1).take(limit - 1)
        }
        return (listOf(track) + current).take(limit)
    }

    /**
     * Attaches [artworkUrl] to the most recent entry, but only when that entry is the
     * same hearing — same title, artist and station. Never adds a row.
     *
     * Cover art is looked up after a track is recorded and can finish after the listener
     * has switched stations (the switch updates the station before any new metadata
     * arrives). Recording it as a new hearing would file the old track under the new
     * station and count it twice in Top Tracks, so an answer that no longer matches the
     * front entry is dropped.
     */
    fun attachArtwork(
        current: List<HeardTrack>,
        title: String,
        artist: String?,
        stationId: String?,
        artworkUrl: String,
    ): List<HeardTrack> {
        val front = current.firstOrNull() ?: return current
        if (front.title != title || front.artist != artist || front.stationId != stationId) return current
        if (front.artworkUrl == artworkUrl) return current
        return listOf(front.copy(artworkUrl = artworkUrl)) + current.drop(1)
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
