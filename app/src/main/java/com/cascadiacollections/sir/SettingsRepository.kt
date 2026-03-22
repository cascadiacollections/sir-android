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
 * Stream quality options — URLs point to the same SHOUTcast mount; the server
 * auto-selects the highest available bitrate for /stream. Add alternate mount
 * paths here if the station exposes them (e.g., /stream_lo for 64kbps).
 */
enum class StreamQuality(val label: String, val url: String) {
    HIGH("High (default)", "https://broadcast.shoutcheap.com/proxy/willradio/stream"),
    MEDIUM("Medium", "https://broadcast.shoutcheap.com/proxy/willradio/stream"),
    LOW("Low", "https://broadcast.shoutcheap.com/proxy/willradio/stream");

    companion object {
        fun fromOrdinal(ordinal: Int): StreamQuality = entries.getOrNull(ordinal) ?: HIGH
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
            entries.getOrNull(ordinal) ?: NORMAL
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

    // Now-playing history (pipe-delimited: timestamp|title|artist, one per line, newest first)
    private val nowPlayingHistoryKey = stringPreferencesKey("now_playing_history")

    data class HistoryEntry(val timestamp: Long, val title: String, val artist: String?)

    val nowPlayingHistory: Flow<List<HistoryEntry>> = context.dataStore.data.map { prefs ->
        prefs[nowPlayingHistoryKey]
            ?.splitToSequence('\n')
            ?.mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                val ts = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val title = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HistoryEntry(ts, title, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
            ?.toList()
            ?: emptyList()
    }

    suspend fun addHistoryEntry(title: String, artist: String?) {
        if (title.isBlank()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[nowPlayingHistoryKey]
                ?.split('\n')?.toMutableList() ?: mutableListOf()
            // Deduplicate consecutive identical tracks
            val newLine = "${System.currentTimeMillis()}|$title|${artist.orEmpty()}"
            val lastTitle = existing.firstOrNull()?.split('|')?.getOrNull(1)
            if (lastTitle != title) {
                existing.add(0, newLine)
                if (existing.size > 50) existing.subList(50, existing.size).clear()
                prefs[nowPlayingHistoryKey] = existing.joinToString("\n")
            }
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

