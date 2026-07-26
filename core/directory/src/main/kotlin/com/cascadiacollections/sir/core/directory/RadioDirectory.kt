package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery

/**
 * Boundary between the app and whatever station catalogue backs it.
 *
 * Implementations are composed as decorators (caching, curated fallback, ...) so the
 * consumer only ever sees this interface. This mirrors the `RadioDirectoryProviding`
 * protocol used by the ShoutKit iOS client.
 */
interface RadioDirectory {

    /** Free-text search across station names. */
    suspend fun search(query: StationQuery): Result<List<Station>>

    /** Most popular stations, used to seed the browse surface. */
    suspend fun topStations(limit: Int = StationQuery.DEFAULT_LIMIT): Result<List<Station>>

    /** Stations carrying the given directory tag (genre, mood, ...). */
    suspend fun stationsByTag(tag: String, limit: Int = StationQuery.DEFAULT_LIMIT): Result<List<Station>>
}

/** Convenience overload so callers do not have to build a [StationQuery] by hand. */
suspend fun RadioDirectory.search(
    text: String,
    limit: Int = StationQuery.DEFAULT_LIMIT
): Result<List<Station>> = search(StationQuery(text, limit))
