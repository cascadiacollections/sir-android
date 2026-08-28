package com.cascadiacollections.sir.core.playback

/** One ICY-resolved track seen during this session, newest tracked with [timestampMillis]. */
data class TrackHistoryEntry(
    val title: String,
    val artist: String?,
    val timestampMillis: Long
)

/**
 * Bounded, deduped history of resolved ICY track metadata ("now playing" over time).
 *
 * A pure list transformation — mirroring how `StationCollections` handles recent
 * stations — so the ordering/capping/dedup rules are testable without Media3, a
 * `MediaSession`, or a live stream.
 */
object TrackHistory {

    /** How many tracks are retained, matching the issue's "25-entry history" ask. */
    const val LIMIT: Int = 25

    /**
     * Records [entry] as the most recent track, newest first, capped at [limit].
     *
     * A repeat of the entry already at the front — the same track re-announced by a
     * metadata refresh rather than a genuine change — is dropped rather than
     * duplicated; ICY servers commonly re-send identical `StreamTitle` on unrelated
     * updates.
     */
    fun record(
        current: List<TrackHistoryEntry>,
        entry: TrackHistoryEntry,
        limit: Int = LIMIT
    ): List<TrackHistoryEntry> {
        require(limit > 0) { "limit must be positive" }
        val front = current.firstOrNull()
        if (front != null && front.title == entry.title && front.artist == entry.artist) {
            return current
        }
        return (listOf(entry) + current).take(limit)
    }
}
