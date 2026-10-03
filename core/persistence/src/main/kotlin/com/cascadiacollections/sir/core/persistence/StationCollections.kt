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

    /** How many recent stations the Browse tab's Recently Played shelf shows (ShoutKit's five). */
    const val RECENT_SHELF_LIMIT: Int = 5

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
     * Moves the favourite at [from] to [to], shifting the stations in between. Out-of-range
     * indices leave the list unchanged rather than throwing: the indices come from UI that
     * can be one emission behind the store.
     */
    fun moveFavorite(current: List<Station>, from: Int, to: Int): List<Station> {
        if (from !in current.indices || to !in current.indices || from == to) return current
        return current.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Reorders favourites to follow [orderedIds] — the order the user dragged them into.
     * Unknown ids are ignored, and saved stations missing from [orderedIds] (saved while
     * the drag was in progress) keep their relative order after the ordered ones, so a
     * stale drag can reorder but never drop a favourite.
     */
    fun reorderFavorites(current: List<Station>, orderedIds: List<String>): List<Station> {
        val byId = current.associateBy { it.id }
        val ordered = orderedIds.distinct().mapNotNull { byId[it] }
        val placed = ordered.mapTo(mutableSetOf()) { it.id }
        return ordered + current.filterNot { it.id in placed }
    }

    /** Outcome of [mergeFavorites]. */
    data class MergeResult(val stations: List<Station>, val added: Int, val skipped: Int)

    /**
     * Merges [imported] favourites into [current] by station id: stations already saved
     * are skipped (the user's copy, which may have been edited, wins) and new ones are
     * appended in import order — ShoutKit's import semantics.
     */
    fun mergeFavorites(current: List<Station>, imported: List<Station>): MergeResult {
        val seen = current.mapTo(mutableSetOf()) { it.id }
        val added = imported.filter { it.id.isNotBlank() && seen.add(it.id) }
        return MergeResult(
            stations = current + added,
            added = added.size,
            skipped = imported.size - added.size,
        )
    }

    /**
     * Finds a station by name for voice search ("Play [station name]"): an exact
     * case-insensitive match first, falling back to a substring match, so "Play NPR"
     * finds a station named exactly "NPR" before matching "Classical NPR" and doesn't
     * miss "NPR News" just because there's no exact "NPR".
     *
     * [query] is trimmed before matching — a voice assistant can hand back leading or
     * trailing whitespace — and a query that's blank after trimming never matches,
     * since `contains("")` would otherwise match the first station in [current].
     */
    fun findByName(current: List<Station>, query: String): Station? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        current.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        return current.firstOrNull { it.name.contains(trimmed, ignoreCase = true) }
    }

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

    /**
     * The Recently Played shelf: the newest [limit] recents minus the ones the user hid.
     *
     * The window is taken *before* hidden stations are removed, so hiding a tile leaves a
     * gap instead of pulling the next-oldest station up into it (ShoutKit's behaviour) —
     * the shelf is "what you played last", not "the last five you didn't hide".
     */
    fun recentShelf(
        recents: List<Station>,
        hiddenIds: Set<String>,
        limit: Int = RECENT_SHELF_LIMIT
    ): List<Station> = recents.take(limit).filterNot { it.id in hiddenIds }

    /**
     * Hidden shelf ids after [played] is selected: playing a station again un-hides it,
     * and ids no longer in [recents] are dropped so the set never outgrows the recents list.
     */
    fun hiddenAfterPlay(hiddenIds: Set<String>, recents: List<Station>, played: Station): Set<String> {
        val recentIds = recents.mapTo(HashSet()) { it.id }
        return hiddenIds.filterTo(LinkedHashSet()) { it != played.id && it in recentIds }
    }
}
