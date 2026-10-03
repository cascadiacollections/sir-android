package com.cascadiacollections.sir.core.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single live [DataStore] for the track-history file.
 *
 * Kept apart from the settings file: up to [HeardTracks.LIMIT] entries is a JSON blob a
 * couple of orders of magnitude larger than every setting combined, and DataStore rewrites
 * the whole file on each edit — so sharing a file would make every settings toggle pay
 * for the history, and every track change rewrite the settings.
 *
 * Owner tracking follows `SettingsDataStore` (see there and docs/architecture.md): one
 * store per Application, and the previous store's scope is cancelled before a new
 * Application's store is built, so Robolectric's fresh Application per test doesn't leave
 * two stores contending for one file.
 */
private object TrackHistoryDataStore {

    private const val FILE_NAME = "track_history"

    private var owner: Context? = null
    private var ownerScope: CoroutineScope? = null
    private var instance: DataStore<Preferences>? = null

    @Synchronized
    operator fun get(context: Context): DataStore<Preferences> {
        val app = context.applicationContext
        instance?.let { existing -> if (owner === app) return existing }

        ownerScope?.cancel()

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val created = PreferenceDataStoreFactory.create(scope = scope) {
            app.preferencesDataStoreFile(FILE_NAME)
        }
        owner = app
        ownerScope = scope
        instance = created
        return created
    }
}

/**
 * Persisted "Recently Heard" track history, newest first.
 *
 * Written by the playback service, where ICY metadata is resolved, so history accrues
 * whether or not any UI is alive; read by the player's history sheet and the Library.
 */
class TrackHistoryRepository(context: Context) {

    private val dataStore = TrackHistoryDataStore[context]
    private val tracksKey = stringPreferencesKey("tracks")

    /** Every retained track, newest first. */
    val tracks: Flow<List<HeardTrack>> = dataStore.data.map { preferences ->
        HeardTracks.decode(preferences[tracksKey])
    }

    /** Records [track], merging a consecutive repeat, in one transaction. */
    suspend fun record(track: HeardTrack) {
        dataStore.edit { preferences ->
            val current = HeardTracks.decode(preferences[tracksKey])
            val updated = HeardTracks.record(current, track)
            if (updated != current) preferences[tracksKey] = HeardTracks.encode(updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(tracksKey) }
    }
}
