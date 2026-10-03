package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import kotlinx.coroutines.flow.Flow

/**
 * The slice of settings behind the Browse tab's Recently Played shelf, so the screen's
 * ViewModel can be tested against a fake instead of DataStore.
 *
 * Hiding affects the shelf only: the station stays in [recentStations] (and so in the
 * Library's play history). [SettingsRepository.selectStation] un-hides a station when it is
 * played again, in the same transaction that records the play.
 */
interface RecentShelfStore {
    /** Recently played stations, newest first. */
    val recentStations: Flow<List<Station>>

    /** Ids the user removed from the shelf. */
    val hiddenRecentStationIds: Flow<Set<String>>

    /** Hides [stationId] from the shelf; a no-op for a station not in the recents. */
    suspend fun hideRecentStation(stationId: String)

    /** Undoes [hideRecentStation]. */
    suspend fun unhideRecentStation(stationId: String)
}
