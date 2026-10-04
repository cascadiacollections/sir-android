package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.flow.Flow

/**
 * Degrades gracefully to [CuratedStations] when the wrapped directory fails.
 *
 * Deliberately sits *outside* [CachingRadioDirectory] so a fallback response is never
 * cached and the next attempt still reaches the network.
 */
class CuratedFallbackDirectory(
    private val delegate: RadioDirectory,
    private val curated: List<Station> = CuratedStations.ALL,
    private val curatedTags: List<Tag> = Tag.CURATED
) : RadioDirectory {

    override suspend fun search(query: StationQuery): Result<List<Station>> =
        search(query, StationSearchFilters.NONE)

    override suspend fun search(query: StationQuery, filters: StationSearchFilters): Result<List<Station>> =
        delegate.search(query, filters).orCurated {
            filters.applyTo(CuratedStations.matching(query.normalizedText, curated))
        }

    override suspend fun topStations(limit: Int): Result<List<Station>> =
        delegate.topStations(limit).orCurated { curated.take(limit) }

    /**
     * A forced refresh is passed through without a fallback: the user asked for live data,
     * and a failure has to reach the caller so it can keep what it shows and say so.
     */
    override suspend fun topStations(limit: Int, forceRefresh: Boolean): Result<List<Station>> =
        if (forceRefresh) delegate.topStations(limit, forceRefresh = true) else topStations(limit)

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> =
        stationsByTag(tag, limit, StationSearchFilters.NONE)

    override suspend fun stationsByTag(
        tag: String,
        limit: Int,
        filters: StationSearchFilters
    ): Result<List<Station>> =
        delegate.stationsByTag(tag, limit, filters).orCurated {
            filters.applyTo(CuratedStations.matching(tag, curated)).take(limit)
        }

    /** The genre list is never empty: a failure falls back to the bundled genres. */
    override suspend fun topTags(limit: Int): Result<List<Tag>> =
        delegate.topTags(limit).recoverCatching { error ->
            curatedTags.take(limit.coerceAtLeast(1)).ifEmpty { throw error }
        }

    /** As the [topStations] overload: a forced refresh is never answered from bundled genres. */
    override suspend fun topTags(limit: Int, forceRefresh: Boolean): Result<List<Tag>> =
        if (forceRefresh) delegate.topTags(limit, forceRefresh = true) else topTags(limit)

    /** Passed straight through: a click report has no meaningful fallback. */
    override suspend fun reportClick(stationId: String): Result<Unit> = delegate.reportClick(stationId)

    override suspend fun getStation(id: String): Result<Station?> =
        delegate.getStation(id).recoverCatching { curated.firstOrNull { it.id == id } }

    /**
     * No fallback: bundled data says nothing about a saved station's current stream, and a
     * failure must reach the background refresh so it leaves the saved stations alone.
     */
    override suspend fun getStations(ids: List<String>): Result<List<Station>> = delegate.getStations(ids)

    override val discoveryUpdates: Flow<DiscoveryUpdate> get() = delegate.discoveryUpdates

    /**
     * Only failures fall back. An empty *successful* response is a real answer ("no
     * such station") and must not be masked by curated content.
     */
    private inline fun Result<List<Station>>.orCurated(
        fallback: () -> List<Station>
    ): Result<List<Station>> = recoverCatching { error ->
        fallback().ifEmpty { throw error }
    }
}
