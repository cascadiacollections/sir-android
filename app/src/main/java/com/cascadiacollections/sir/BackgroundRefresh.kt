package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.StationIds
import com.cascadiacollections.sir.core.persistence.SavedStationRefreshStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * One background refresh pass, as ShoutKit's `BackgroundRefreshController` runs it:
 * re-fetch discovery (popular stations and genres — the directory chain's snapshot layer
 * persists them) and refresh saved stations' stream metadata with one batched `byuuid`
 * lookup. Kept apart from [BackgroundRefreshWorker] so it is testable without WorkManager.
 *
 * Never reports clicks: nothing here is a user starting a station.
 */
class BackgroundRefresh(
    private val directory: RadioDirectory,
    private val favorites: SavedStationRefreshStore
) {

    /**
     * [favoritesUpdated] is null when there was nothing to refresh (no saved radio-browser
     * stations), a failure when the lookup failed, else how many saved stations changed.
     */
    data class Outcome(
        val discoveryRefreshed: Boolean,
        val favoritesUpdated: Result<Int>?
    ) {
        /** Nothing that was attempted succeeded, so the pass is worth retrying. */
        val shouldRetry: Boolean
            get() = !discoveryRefreshed && (favoritesUpdated == null || favoritesUpdated.isFailure)
    }

    suspend fun run(): Outcome = coroutineScope {
        // forceRefresh: past every cache, and a failure is not masked by bundled data.
        val stations = async { directory.topStations(SearchViewModel.POPULAR_LIMIT, forceRefresh = true) }
        val tags = async { directory.topTags(RadioDirectory.DEFAULT_TAG_LIMIT, forceRefresh = true) }
        val favoritesUpdated = refreshFavorites()
        Outcome(
            discoveryRefreshed = stations.await().isSuccess || tags.await().isSuccess,
            favoritesUpdated = favoritesUpdated
        )
    }

    private suspend fun refreshFavorites(): Result<Int>? {
        val ids = favorites.savedStations.first().map { it.id }.filter(StationIds::isRadioBrowserUuid)
        if (ids.isEmpty()) return null
        return directory.getStations(ids).map { fetched -> favorites.refreshSavedStations(fetched) }
    }
}
