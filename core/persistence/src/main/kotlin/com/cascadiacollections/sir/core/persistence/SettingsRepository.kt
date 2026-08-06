package com.cascadiacollections.sir.core.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.SleepTimerDuration
import com.cascadiacollections.sir.core.playback.StreamQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * The single live [DataStore] for the settings file.
 *
 * DataStore requires that a file is owned by one instance at a time, which the
 * `preferencesDataStore` property delegate guarantees by caching one instance forever.
 * That delegate caches it against the *first* context it ever sees, though, which breaks
 * under Robolectric: a test class can get a fresh Application with a fresh files
 * directory, leaving the cached store writing through a directory that no longer exists.
 *
 * So the owning Application is tracked explicitly. In production it is set once and never
 * changes. When a *different* Application appears, the previous store's scope is cancelled
 * before the replacement is built — DataStore only releases its claim on the file when
 * that scope completes, so without the cancel the second store would either contend with
 * the first or trip the "multiple DataStores active for the same file" check.
 *
 * This used to be a `WeakHashMap<Context, DataStore<Preferences>>`, which looked like it
 * bounded itself but did not: the cached value's `produceFile` lambda closes over the very
 * context used as the key, so no entry was ever weakly reachable and no scope was ever
 * cancelled. One strongly-held owner is both smaller and honest about its lifetime.
 */
private object SettingsDataStore {

    private const val FILE_NAME = "settings"

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

private val Context.dataStore: DataStore<Preferences> get() = SettingsDataStore[this]

/**
 * Settings repository using DataStore for persistence.
 */
class SettingsRepository(private val context: Context) {

    private val streamQualityKey = intPreferencesKey("stream_quality")
    private val chromecastEnabledKey = booleanPreferencesKey("chromecast_enabled")
    private val sleepTimerMinutesKey = intPreferencesKey("sleep_timer_minutes")
    private val sleepTimerFiresAtKey = longPreferencesKey("sleep_timer_fires_at")
    private val equalizerPresetKey = intPreferencesKey("equalizer_preset")
    private val customStreamUrlKey = stringPreferencesKey("custom_stream_url")
    private val savedStationsKey = stringPreferencesKey("saved_stations")
    private val recentStationsKey = stringPreferencesKey("recent_stations")
    private val selectedStationKey = stringPreferencesKey("selected_station")
    private val stationPlayCountsKey = stringPreferencesKey("station_play_counts")
    private val connectionPrewarmingEnabledKey = booleanPreferencesKey("connection_prewarming_enabled")

    val streamQuality: Flow<StreamQuality> = context.dataStore.data.map { prefs ->
        StreamQuality.fromOrdinal(prefs[streamQualityKey] ?: 0)
    }

    suspend fun setStreamQuality(quality: StreamQuality) {
        context.dataStore.edit { prefs -> prefs[streamQualityKey] = quality.ordinal }
    }

    /**
     * Flow of Chromecast enabled preference
     */
    val chromecastEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[chromecastEnabledKey] ?: false
    }

    /**
     * Set Chromecast enabled preference
     */
    suspend fun setChromecastEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[chromecastEnabledKey] = enabled
        }
    }

    /**
     * Flow of sleep timer duration
     */
    val sleepTimerDuration: Flow<SleepTimerDuration> = context.dataStore.data.map { preferences ->
        SleepTimerDuration.fromMinutes(preferences[sleepTimerMinutesKey] ?: 0)
    }

    /**
     * Set sleep timer duration
     */
    suspend fun setSleepTimerDuration(duration: SleepTimerDuration) {
        context.dataStore.edit { preferences ->
            preferences[sleepTimerMinutesKey] = duration.minutes
        }
    }

    /**
     * Flow of the epoch-millis timestamp when the sleep timer will fire (0 = no timer active)
     */
    val sleepTimerFiresAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[sleepTimerFiresAtKey] ?: 0L
    }

    /**
     * Persist when the sleep timer will fire; pass 0 or negative to clear
     */
    suspend fun setSleepTimerFiresAt(epochMillis: Long) {
        context.dataStore.edit { prefs ->
            if (epochMillis <= 0L) prefs.remove(sleepTimerFiresAtKey)
            else prefs[sleepTimerFiresAtKey] = epochMillis
        }
    }

    /**
     * Flow of equalizer preset
     */
    val equalizerPreset: Flow<EqualizerPreset> = context.dataStore.data.map { preferences ->
        EqualizerPreset.fromOrdinal(preferences[equalizerPresetKey] ?: 0)
    }

    /**
     * Set equalizer preset
     */
    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        context.dataStore.edit { preferences ->
            preferences[equalizerPresetKey] = preset.ordinal
        }
    }

    /**
     * Flow of custom stream URL (debug only feature)
     */
    val customStreamUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[customStreamUrlKey]?.takeIf { it.isNotBlank() }
    }

    /**
     * Set custom stream URL (debug only feature)
     */
    suspend fun setCustomStreamUrl(url: String?) {
        context.dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(customStreamUrlKey)
            } else {
                preferences[customStreamUrlKey] = url
            }
        }
    }

    /**
     * Flow of the user's saved (favourite) stations.
     */
    val savedStations: Flow<List<Station>> = context.dataStore.data.map { preferences ->
        StationCodec.decode(preferences[savedStationsKey])
    }

    /** Saved stations ordered by play count, with saved order breaking ties. */
    val mostPlayedSavedStations: Flow<List<Station>> = context.dataStore.data.map { preferences ->
        val counts = decodePlayCounts(preferences[stationPlayCountsKey])
        StationCodec.decode(preferences[savedStationsKey])
            .sortedByDescending { counts[it.id] ?: 0 }
    }

    /** Whether speculative connection prewarming is enabled. Disabled until measured. */
    val connectionPrewarmingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[connectionPrewarmingEnabledKey] ?: false
    }

    suspend fun setConnectionPrewarmingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[connectionPrewarmingEnabledKey] = enabled
        }
    }

    /**
     * Flow of recently played stations, newest first.
     */
    val recentStations: Flow<List<Station>> = context.dataStore.data.map { preferences ->
        StationCodec.decode(preferences[recentStationsKey])
    }

    /**
     * Adds a station to favourites, refreshing directory metadata if already saved.
     */
    suspend fun saveStation(station: Station) = editStations(savedStationsKey) { current ->
        StationCollections.addFavorite(current, station)
    }

    /**
     * Removes a favourite by station id.
     */
    suspend fun removeStation(stationId: String) = editStations(savedStationsKey) { current ->
        StationCollections.removeFavorite(current, stationId)
    }

    // No standalone `recordRecentStation`: recents are written by `selectStation` inside
    // the same transaction as the selection. A separate entry point could record a play
    // the selection never saw, which is exactly the divergence that transaction prevents.

    /**
     * The station the user last chose to play, or `null` when playing the app's own
     * stream. Persisted so the choice survives process death and is visible to the
     * playback service, the widget and the tile without an explicit hand-off.
     */
    val selectedStation: Flow<Station?> = context.dataStore.data.map { preferences ->
        StationCodec.decode(preferences[selectedStationKey]).firstOrNull()
    }

    /**
     * Selects [station] for playback and records it as recently played.
     *
     * Both writes happen in one transaction so a crash can never leave a station
     * playing that is missing from the recents list.
     */
    suspend fun selectStation(station: Station) {
        context.dataStore.edit { preferences ->
            preferences[selectedStationKey] = StationCodec.encode(listOf(station))
            val counts = decodePlayCounts(preferences[stationPlayCountsKey])
            preferences[stationPlayCountsKey] = Json.encodeToString(
                counts + (station.id to (counts[station.id]?.let {
                    if (it == Int.MAX_VALUE) it else it + 1
                } ?: 1))
            )
            val recents = StationCollections.recordRecent(
                StationCodec.decode(preferences[recentStationsKey]),
                station
            )
            preferences[recentStationsKey] = StationCodec.encode(recents)
        }
    }

    /**
     * Reverts to the app's own stream.
     */
    suspend fun clearSelectedStation() {
        context.dataStore.edit { preferences -> preferences.remove(selectedStationKey) }
    }

    /**
     * Clears the recently played list, e.g. from the privacy settings.
     */
    suspend fun clearRecentStations() {
        context.dataStore.edit { preferences -> preferences.remove(recentStationsKey) }
    }

    /**
     * Read-modify-write inside a single DataStore transaction so concurrent edits
     * from the UI and the playback service cannot clobber each other.
     */
    private suspend fun editStations(
        key: Preferences.Key<String>,
        transform: (List<Station>) -> List<Station>
    ) {
        context.dataStore.edit { preferences ->
            val updated = transform(StationCodec.decode(preferences[key]))
            preferences[key] = StationCodec.encode(updated)
        }
    }

    /** Play counts keyed by station id; unreadable or negative entries are discarded. */
    private fun decodePlayCounts(raw: String?): Map<String, Int> =
        raw?.let { runCatching { Json.decodeFromString<Map<String, Int>>(it) }.getOrNull() }
            ?.filterValues { it >= 0 }
            ?: emptyMap()
}
