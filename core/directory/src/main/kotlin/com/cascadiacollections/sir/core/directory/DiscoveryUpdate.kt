package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station

/** A fresher discovery answer than the one a caller was given; see [RadioDirectory.discoveryUpdates]. */
sealed interface DiscoveryUpdate {
    /** New [RadioDirectory.topStations] for [limit]. */
    data class TopStations(val limit: Int, val stations: List<Station>) : DiscoveryUpdate

    /** New [RadioDirectory.topTags] for [limit]. */
    data class TopTags(val limit: Int, val tags: List<Tag>) : DiscoveryUpdate
}
