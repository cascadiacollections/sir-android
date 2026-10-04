package com.cascadiacollections.sir

import android.content.Intent
import android.net.Uri
import com.cascadiacollections.sir.core.directory.CuratedStations
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.StationIds
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * `sir://` links, shared by every entry point so they all resolve and play a linked
 * station the same way:
 *
 * - `sir://station/{id}` (station shortcuts) opens the app — the phone or TV activity —
 *   on that station.
 * - `sir://play/{id}` and `sir://play` (automation: routines, Tasker, pinned shortcuts)
 *   start playback without any UI; see [PlayStationActivity].
 */
internal object StationDeepLink {
    private const val SCHEME = "sir"
    private const val HOST_STATION = "station"
    private const val HOST_PLAY = "play"

    /**
     * Station ids arrive from other apps, so anything longer than a URL could sensibly be
     * is refused before it reaches DataStore or a lookup.
     */
    private const val MAX_ID_LENGTH = 2048

    // Bundled stations: `sir-default` and `curated-*` (see CuratedStations).
    private val BUNDLED_ID = Regex("^(sir-default|curated-[a-z0-9-]+)$")
    private const val IMPORTED_ID_PREFIX = "imported:"

    /** The station id an [Intent] links to, or null when it isn't a station link. */
    fun stationId(intent: Intent?): String? {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST_STATION) return null
        return uri.lastPathSegment?.takeIf { it.isNotBlank() }
    }

    /** What an automation link asked for. */
    sealed interface PlayRequest {
        /** `sir://play`: resume, or play the last selected station. */
        data object Resume : PlayRequest

        /** `sir://play/{id}`: select [id] and play it. */
        data class Station(val id: String) : PlayRequest

        /** A play link whose id is not one SIR could ever have issued. */
        data object Invalid : PlayRequest
    }

    /**
     * Parses a [PlayStationActivity] intent: [PlayStationActivity.ACTION_PLAY_STATION] with
     * an optional `sir://play/{id}` data URI, or a VIEW of that same URI. Null when the
     * intent is neither. Everything in it is untrusted (any app can send it), so the id is
     * checked against [isValidId] here and nothing else from the intent is read.
     */
    fun playRequest(intent: Intent?): PlayRequest? {
        val action = intent?.action
        val uri = intent?.data
        val isPlayAction = action == PlayStationActivity.ACTION_PLAY_STATION
        return when {
            !isPlayAction && action != Intent.ACTION_VIEW -> null

            // The explicit action alone means "play"; a VIEW needs something to view.
            uri == null -> PlayRequest.Resume.takeIf { isPlayAction }

            uri.scheme != SCHEME || uri.host != HOST_PLAY -> null

            else -> playRequest(uri.pathSegments)
        }
    }

    private fun playRequest(segments: List<String>): PlayRequest = when {
        segments.isEmpty() -> PlayRequest.Resume
        segments.size == 1 && isValidId(segments[0]) -> PlayRequest.Station(segments[0])
        else -> PlayRequest.Invalid
    }

    /**
     * Whether [id] has the shape of a station id SIR issues: a radio-browser UUID, a bundled
     * station, or an imported stream (`imported:` + its http(s) URL). Other strings can't
     * name a station, so they never reach a directory request or the saved-station lookup.
     */
    fun isValidId(id: String): Boolean {
        val wellFormed = id.length in 1..MAX_ID_LENGTH && id.none { it.isISOControl() || it.isWhitespace() }
        return wellFormed && (StationIds.isRadioBrowserUuid(id) || BUNDLED_ID.matches(id) || isImportedId(id))
    }

    private fun isImportedId(id: String): Boolean {
        val url = id.removePrefix(IMPORTED_ID_PREFIX).takeIf { it != id }.orEmpty()
        return url.startsWith("http://") || url.startsWith("https://")
    }

    /** `sir://play/{id}` (or `sir://play` with no id) — the link automation apps open. */
    fun playLink(stationId: String? = null): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST_PLAY)
        // appendPath percent-encodes, so an imported id's ':' and '/' survive as one segment.
        .apply { stationId?.let(::appendPath) }
        .build()

    /** `sir://station/{id}`, which opens the app on the station. */
    fun stationLink(stationId: String): Uri =
        Uri.Builder().scheme(SCHEME).authority(HOST_STATION).appendPath(stationId).build()

    /**
     * Selects (and so starts playing) the linked station: the directory's current record
     * when it has one, else the saved copy, then a recently played one, then a bundled
     * station. Returns whether a playable station was found.
     *
     * Only radio-browser UUIDs are looked up in the directory — any other id would only
     * send a request that can't match — so bundled and imported stations, and every saved
     * or recent station when offline, resolve without the network.
     */
    suspend fun play(id: String, directory: RadioDirectory, repository: SettingsRepository): Boolean {
        val station = directory.takeIf { StationIds.isRadioBrowserUuid(id) }?.getStation(id)?.getOrNull()
            ?: repository.savedStations.first().firstOrNull { it.id == id }
            ?: repository.recentStations.first().firstOrNull { it.id == id }
            ?: CuratedStations.ALL.firstOrNull { it.id == id }
        if (station == null || !station.isPlayable) return false
        repository.selectStation(station)
        return true
    }
}
