package com.cascadiacollections.sir

import android.content.Intent
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * `sir://station/{id}` links (station shortcuts), shared by the phone and TV activities so
 * both resolve and play a linked station the same way.
 */
internal object StationDeepLink {
    private const val SCHEME = "sir"
    private const val HOST_STATION = "station"

    /** The station id an [Intent] links to, or null when it isn't a station link. */
    fun stationId(intent: Intent?): String? {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST_STATION) return null
        return uri.lastPathSegment?.takeIf { it.isNotBlank() }
    }

    /**
     * Selects (and so starts playing) the linked station: the directory's current record
     * when it has one, else the saved copy. Returns whether a playable station was found.
     */
    suspend fun play(id: String, directory: RadioDirectory, repository: SettingsRepository): Boolean {
        val station = directory.getStation(id).getOrNull()
            ?: repository.savedStations.first().firstOrNull { it.id == id }
        if (station == null || !station.isPlayable) return false
        repository.selectStation(station)
        return true
    }
}
