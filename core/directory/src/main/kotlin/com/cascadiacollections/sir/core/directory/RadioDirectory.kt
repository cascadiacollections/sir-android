package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Boundary between the app and whatever station catalogue backs it.
 *
 * Implementations are composed as decorators (caching, curated fallback, ...) so the
 * consumer only ever sees this interface. This mirrors the `RadioDirectoryProviding`
 * protocol used by the ShoutKit iOS client.
 *
 * The filtered overloads, [topTags] and [reportClick] have defaults so simple
 * implementations (test fakes) stay small; every production decorator overrides all of
 * them explicitly so nothing is silently dropped on the way to the network.
 */
interface RadioDirectory {

    /** Free-text search across station names, most-clicked first. */
    suspend fun search(query: StationQuery): Result<List<Station>>

    /**
     * Free-text search refined by [filters]. The default ignores the server and filters
     * the unfiltered result locally.
     */
    suspend fun search(query: StationQuery, filters: StationSearchFilters): Result<List<Station>> =
        search(query).map(filters::applyTo)

    /** Most popular stations, used to seed the browse surface. */
    suspend fun topStations(limit: Int = StationQuery.DEFAULT_LIMIT): Result<List<Station>>

    /**
     * [topStations] for a user-initiated refresh (pull-to-refresh). With [forceRefresh] a
     * cache must not answer and a fallback must not mask a failure: the caller already has
     * stations on screen and decides itself what to keep. The default has no cache to skip.
     */
    suspend fun topStations(limit: Int, forceRefresh: Boolean): Result<List<Station>> = topStations(limit)

    /** Stations carrying the given directory tag (genre, mood, ...), most-clicked first. */
    suspend fun stationsByTag(tag: String, limit: Int = StationQuery.DEFAULT_LIMIT): Result<List<Station>>

    /** Genre browse refined by [filters]; [StationSearchFilters.tag] narrows within [tag]. */
    suspend fun stationsByTag(tag: String, limit: Int, filters: StationSearchFilters): Result<List<Station>> =
        stationsByTag(tag, limit).map(filters::applyTo)

    /** A single station by its radio-browser `stationuuid`, or null if unknown. */
    suspend fun getStation(id: String): Result<Station?>

    /**
     * Several stations by radio-browser `stationuuid`, in one request per
     * [MAX_BATCH_IDS] ids where the backend supports it. Unknown ids, and ids that are
     * not radio-browser UUIDs, are simply absent from the result; order is not
     * guaranteed. Used to refresh saved stations' stream metadata in the background, so
     * it is never cached and never answered from fallback data.
     *
     * The default looks each id up with [getStation] and fails if any lookup fails.
     */
    suspend fun getStations(ids: List<String>): Result<List<Station>> = runCatching {
        ids.distinct().mapNotNull { id -> getStation(id).getOrThrow() }
    }

    /**
     * Discovery results ([topStations]/[topTags]) that arrived *after* the caller was
     * answered — a stale-while-revalidate refresh or the background worker — so a screen
     * already showing the older answer can replace it. Empty unless a layer of the chain
     * persists discovery (see `SnapshotRadioDirectory`).
     */
    val discoveryUpdates: Flow<DiscoveryUpdate> get() = emptyFlow()

    /** The most-used tags, by station count, for the genre list. */
    suspend fun topTags(limit: Int = DEFAULT_TAG_LIMIT): Result<List<Tag>> = Result.success(emptyList())

    /** [topTags] with the same [forceRefresh] contract as the [topStations] overload. */
    suspend fun topTags(limit: Int, forceRefresh: Boolean): Result<List<Tag>> = topTags(limit)

    /**
     * Tells radio-browser that the user started [stationId] (`GET /json/url/{uuid}`), as
     * its API guidance asks for every user click. The server counts one click per
     * IP/station/day, so callers report every explicit play and need not de-duplicate.
     *
     * Never cached and never answered from fallback data. Ids that are not radio-browser
     * UUIDs (bundled or imported stations) succeed without a request.
     */
    suspend fun reportClick(stationId: String): Result<Unit> = Result.success(Unit)

    companion object {
        /** ShoutKit's genre list size. */
        const val DEFAULT_TAG_LIMIT: Int = 48

        /** Ids per `byuuid` request in [getStations]; keeps the URL comfortably short. */
        const val MAX_BATCH_IDS: Int = 100
    }
}

/** Convenience overload so callers do not have to build a [StationQuery] by hand. */
suspend fun RadioDirectory.search(
    text: String,
    limit: Int = StationQuery.DEFAULT_LIMIT,
    filters: StationSearchFilters = StationSearchFilters.NONE
): Result<List<Station>> = search(StationQuery(text, limit), filters)
