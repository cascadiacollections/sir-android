package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import kotlinx.coroutines.flow.Flow

/**
 * How a background directory refresh (ShoutKit's `BackgroundRefreshController`) may update
 * saved stations without undoing anything the user did.
 *
 * There is no "edited" flag on [Station], so the rule is defined by which fields the user
 * can change in the edit sheet — name, stream URL and artwork URL — and what an edit leaves
 * behind:
 * - [Station.name] and [Station.url] are never touched.
 * - The directory's stream description (`url_resolved`, `hls`, `bitrate`, `codec`) is only
 *   taken when the directory still lists the *same* [Station.url] the user has saved. An
 *   edited URL ([Station.withUrl]) no longer matches, and since playback prefers
 *   `url_resolved` over `url`, adopting it would silently play the stream the user edited
 *   away from. A station the directory moved to a new URL is likewise left alone.
 * - [Station.favicon] is only filled in when the saved one is blank: a non-blank artwork
 *   URL may be the user's, and nothing distinguishes it from the directory's.
 *
 * Order, ids and every other field are preserved; stations missing from the directory's
 * answer (deleted, broken, or not radio-browser stations) are left exactly as saved.
 */
object SavedStationRefresh {

    /** [stations] after the refresh, and how many of them changed. */
    data class Result(val stations: List<Station>, val updated: Int)

    fun merge(saved: List<Station>, fetched: List<Station>): Result {
        if (saved.isEmpty() || fetched.isEmpty()) return Result(saved, 0)
        val byId = fetched.filter { it.id.isNotBlank() }.associateBy { it.id }
        var updated = 0
        val merged = saved.map { station ->
            val fresh = byId[station.id] ?: return@map station
            val refreshed = refresh(station, fresh)
            if (refreshed != station) updated++
            refreshed
        }
        return Result(if (updated == 0) saved else merged, updated)
    }

    private fun refresh(saved: Station, fresh: Station): Station {
        val sameStream = fresh.url.isNotBlank() && fresh.url == saved.url
        val streamRefreshed = if (sameStream && fresh.isPlayable) {
            saved.copy(
                urlResolved = fresh.urlResolved,
                hls = fresh.hls,
                bitrate = fresh.bitrate,
                codec = fresh.codec
            )
        } else {
            saved
        }
        val freshFavicon = fresh.favicon?.takeIf { it.isNotBlank() }
        return if (saved.favicon.isNullOrBlank() && freshFavicon != null) {
            streamRefreshed.copy(favicon = freshFavicon)
        } else {
            streamRefreshed
        }
    }
}

/**
 * The slice of settings the background refresh needs, so it can be tested against a fake
 * instead of DataStore.
 */
interface SavedStationRefreshStore {
    /** The user's saved stations, in their order. */
    val savedStations: Flow<List<Station>>

    /**
     * Applies [SavedStationRefresh.merge] with [fetched] to the saved stations in one
     * transaction, so a concurrent save, edit or reorder is never lost. Returns how many
     * stations changed; nothing is written when none did.
     */
    suspend fun refreshSavedStations(fetched: List<Station>): Int
}
