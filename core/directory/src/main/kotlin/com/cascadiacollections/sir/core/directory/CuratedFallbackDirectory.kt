package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery

/**
 * Degrades gracefully to [CuratedStations] when the wrapped directory fails.
 *
 * Deliberately sits *outside* [CachingRadioDirectory] so a fallback response is never
 * cached and the next attempt still reaches the network.
 */
class CuratedFallbackDirectory(
    private val delegate: RadioDirectory,
    private val curated: List<Station> = CuratedStations.ALL
) : RadioDirectory {

    override suspend fun search(query: StationQuery): Result<List<Station>> =
        delegate.search(query).orCurated { CuratedStations.matching(query.normalizedText) }

    override suspend fun topStations(limit: Int): Result<List<Station>> =
        delegate.topStations(limit).orCurated { curated.take(limit) }

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> =
        delegate.stationsByTag(tag, limit).orCurated { CuratedStations.matching(tag).take(limit) }

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
