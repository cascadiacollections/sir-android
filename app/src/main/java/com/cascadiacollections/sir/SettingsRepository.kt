package com.cascadiacollections.sir

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Settings repository using DataStore for persistence.
 */
class SettingsRepository(private val context: Context) {

    private val chromecastEnabledKey = booleanPreferencesKey("chromecast_enabled")

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
}

