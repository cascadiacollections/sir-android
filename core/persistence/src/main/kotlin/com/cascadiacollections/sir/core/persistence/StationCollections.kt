package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station

/**
 * Ordering and de-duplication rules for the user's station collections.
 *
 * These are pure list transformations so the storage layer only has to read a string,
 * apply a function and write it back — the interesting behaviour (identity, ordering,
 * capping) is testable without DataStore or Robolectric.
 */
object StationCollections {

    /** How many recently played stations are retained. */
    const val RECENTS_LIMIT: Int = 20

    /**
     * Adds [station] to favourites, or refreshes it in place when already saved.
     *
     * Refreshing in place matters because directory metadata (bitrate, codec, favicon)
     * changes over time, and re-saving a station should not reorder the user's list.
     */
    fun addFavorite(current: List<Station>, station: Station): List<Station> {
        val index = current.indexOfFirst { it.id == station.id }
        return if (index >= 0) {
            current.toMutableList().apply { this[index] = station }
        } else {
            current + station
        }
    }

    fun removeFavorite(current: List<Station>, stationId: String): List<Station> =
        current.filterNot { it.id == stationId }

    /**
     * Records [station] as most recently played: newest first, one entry per station,
     * capped at [limit]. Replaying an existing entry moves it to the front rather than
     * duplicating it.
     */
    fun recordRecent(
        current: List<Station>,
        station: Station,
        limit: Int = RECENTS_LIMIT
    ): List<Station> {
        require(limit > 0) { "limit must be positive" }
        if (!station.isPlayable) return current
        return (listOf(station) + current.filterNot { it.id == station.id }).take(limit)
    }
}
