package com.cascadiacollections.sir

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Available sleep timer durations in minutes
 */
enum class SleepTimerDuration(val minutes: Int, val label: String) {
    OFF(0, "Off"),
    FIFTEEN(15, "15 minutes"),
    THIRTY(30, "30 minutes"),
    SIXTY(60, "1 hour"),
    NINETY(90, "1.5 hours");

    companion object {
        fun fromMinutes(minutes: Int): SleepTimerDuration =
            entries.find { it.minutes == minutes } ?: OFF
    }
}

/**
 * Audio equalizer presets
 */
enum class EqualizerPreset(val label: String) {
    NORMAL("Normal"),
    BASS_BOOST("Bass Boost"),
    VOCAL("Vocal/Podcast"),
    TREBLE("Treble Boost");

    companion object {
        fun fromOrdinal(ordinal: Int): EqualizerPreset =
            entries.getOrElse(ordinal) { NORMAL }
    }
}

/**
 * Settings repository using DataStore for persistence.
 */
class SettingsRepository(private val context: Context) {

    private val chromecastEnabledKey = booleanPreferencesKey("chromecast_enabled")
    private val sleepTimerMinutesKey = intPreferencesKey("sleep_timer_minutes")
    private val equalizerPresetKey = intPreferencesKey("equalizer_preset")
    private val customStreamUrlKey = stringPreferencesKey("custom_stream_url")

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
}

