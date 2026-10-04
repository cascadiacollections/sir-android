package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.model.Station

/**
 * Shape of the Android Auto browse tree, as pure list rules: which categories the root
 * offers, what each one lists, in what order, and how ids are interpreted. ShoutKit's
 * CarPlay has "Your Stations" and "Top Stations" tabs; Auto also gets "Recently Played"
 * (it shows up to four root categories as tabs).
 *
 * Station items use the station id as their media id, so the same station can appear in
 * several categories and still resolve to one thing when it is picked. Nothing here
 * touches Media3 or Android, so ordering, de-duplication and caps are covered on the JVM;
 * [AutoLibrary] turns the result into `MediaItem`s.
 */
internal object AutoBrowseTree {

    /** The library root. Kept from the single-level tree so existing browsers still resolve it. */
    const val ROOT_ID: String = "sir_root"

    /** The bundled SIR stream, listed first under Your Stations. Never a directory id. */
    const val SIR_STREAM_ID: String = "sir_stream"

    /** ShoutKit CarPlay's "Your Stations" cap: favourites, then recents. */
    const val YOUR_STATIONS_LIMIT: Int = 25

    const val RECENTLY_PLAYED_LIMIT: Int = 20

    /** ShoutKit CarPlay's "Top Stations" size. */
    const val TOP_STATIONS_LIMIT: Int = 12

    const val SEARCH_LIMIT: Int = 20

    /** The root's browsable children, in tab order. */
    enum class Category(val id: String) {
        YOUR_STATIONS("auto_your_stations"),
        RECENTLY_PLAYED("auto_recently_played"),
        TOP_STATIONS("auto_top_stations");

        companion object {
            fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }
        }
    }

    /**
     * The root's children. A browser that announces a root-children limit (Auto sends 4)
     * gets at most that many; a non-positive or absent limit means no limit.
     */
    fun rootCategories(childrenLimit: Int?): List<Category> {
        val all = Category.entries
        return if (childrenLimit != null && childrenLimit > 0) all.take(childrenLimit) else all
    }

    /**
     * Saved stations in the user's order, then recently played stations that are not
     * saved, newest first — one entry per station, capped at [limit]. Recents the user hid
     * from the Recently Played shelf stay hidden here too; a saved station is always shown.
     */
    fun yourStations(
        saved: List<Station>,
        recents: List<Station>,
        hiddenRecentIds: Set<String> = emptySet(),
        limit: Int = YOUR_STATIONS_LIMIT,
    ): List<Station> = distinctPlayable(
        saved + recents.filterNot { it.id in hiddenRecentIds },
        limit,
    )

    /** Recently played stations, newest first, minus the hidden ones. */
    fun recentlyPlayed(
        recents: List<Station>,
        hiddenRecentIds: Set<String> = emptySet(),
        limit: Int = RECENTLY_PLAYED_LIMIT,
    ): List<Station> = distinctPlayable(recents.filterNot { it.id in hiddenRecentIds }, limit)

    fun topStations(top: List<Station>, limit: Int = TOP_STATIONS_LIMIT): List<Station> =
        distinctPlayable(top, limit)

    /**
     * Search results: saved stations whose name matches [query] first — they are what the
     * user most likely means and need no network — then the directory's answer.
     */
    fun searchResults(
        query: String,
        saved: List<Station>,
        directoryResults: List<Station>,
        limit: Int = SEARCH_LIMIT,
    ): List<Station> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val savedMatches = saved.filter { it.name.contains(trimmed, ignoreCase = true) }
        return distinctPlayable(savedMatches + directoryResults, limit)
    }

    /** The first station with [id] across [sources], searched in order. */
    fun findStation(id: String, vararg sources: List<Station>): Station? {
        if (id.isBlank()) return null
        return sources.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }
    }

    /**
     * One page of [items]. Media3 passes `page = 0, pageSize = Int.MAX_VALUE` when the
     * browser does not page; a nonsensical request yields an empty page instead of throwing.
     */
    fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
        if (page < 0 || pageSize <= 0) return emptyList()
        val start = page.toLong() * pageSize
        if (start >= items.size) return emptyList()
        val end = minOf(items.size.toLong(), start + pageSize)
        return items.subList(start.toInt(), end.toInt())
    }

    private fun distinctPlayable(stations: List<Station>, limit: Int): List<Station> =
        stations.asSequence()
            .filter { it.isPlayable && it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(limit.coerceAtLeast(0))
            .toList()
}
