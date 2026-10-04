package com.cascadiacollections.sir.wear.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.cascadiacollections.sir.core.model.WatchStationPayload
import com.cascadiacollections.sir.core.model.WatchStationSync
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The watch's copy of what the phone last sent: the last-played station and the recents.
 *
 * A plain [SharedPreferences] file rather than DataStore: it is one small value, written
 * from [StationSyncListenerService] and read synchronously by the tile and the
 * complication, which have no coroutine of their own to wait on.
 */
class WatchStationStore(private val prefs: SharedPreferences) {

    fun load(): WatchStationPayload = WatchStationSync.decode(prefs.getString(KEY, null))

    /**
     * Stores [raw] as received from the phone. It is decoded first and re-encoded, so an
     * unreadable payload is stored as empty rather than poisoning every later read.
     */
    fun save(raw: String?): WatchStationPayload {
        val payload = WatchStationSync.decode(raw)
        prefs.edit { putString(KEY, WatchStationSync.encode(payload)) }
        return payload
    }

    fun clear() {
        prefs.edit { remove(KEY) }
    }

    /** The current payload, then every change to it. */
    val payloads: Flow<WatchStationPayload>
        get() = callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                // A null key is SharedPreferences' "cleared" signal.
                if (key == null || key == KEY) trySend(load())
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(load())
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()

    companion object {
        private const val FILE = "watch_stations"
        private const val KEY = "payload"

        fun from(context: Context): WatchStationStore =
            WatchStationStore(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
