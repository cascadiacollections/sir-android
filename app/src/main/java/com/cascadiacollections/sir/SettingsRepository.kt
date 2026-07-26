package com.cascadiacollections.sir

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.StationCodec
import com.cascadiacollections.sir.core.persistence.StationCollections
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.SleepTimerDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Stream quality options — URLs point to the same SHOUTcast mount; the server
 * auto-selects the highest available bitrate for /stream. Add alternate mount
 * paths here if the station exposes them (e.g., /stream_lo for 64kbps).
 */
enum class StreamQuality(val label: String, val url: String) {
    HIGH("High (default)", StreamConfig.DEFAULT_STREAM_URL),
    MEDIUM("Medium", StreamConfig.DEFAULT_STREAM_URL),
    LOW("Low", StreamConfig.DEFAULT_STREAM_URL);

    companion object {
        fun fromOrdinal(ordinal: Int): StreamQuality = entries.getOrNull(ordinal) ?: HIGH
    }
}

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

    /**
     * Records a station as most recently played.
     */
    suspend fun recordRecentStation(station: Station) = editStations(recentStationsKey) { current ->
        StationCollections.recordRecent(current, station)
    }

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
}
