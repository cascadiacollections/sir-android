package com.cascadiacollections.sir

import android.content.Context
import android.util.Log
import com.cascadiacollections.sir.core.model.WatchStationSync
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await

private const val TAG = "WearStationPublisher"

/**
 * Sends the last-played station and the recents to the Wear app over the Wearable Data
 * Layer, as ShoutKit's phone app does for its watch. One `DataItem` at
 * [WatchStationSync.PATH] holds the whole state, so the watch never has to merge.
 *
 * The Data Layer only delivers between apps with the same applicationId and signing key,
 * and it queues for (or drops, with no node paired) the watch itself, so there is no
 * node discovery here. Play only — the FOSS flavor ships an inert stub with this API.
 */
object WearStationPublisher {

    /** Whether this build can sync to a watch at all. */
    const val isSupported: Boolean = true

    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        WatchStationSyncer(
            selected = settings.selectedStation,
            recents = settings.recentStations,
            publish = { json -> put(app, json) }
        ).start(scope)
    }

    private suspend fun put(context: Context, json: String) {
        // Devices without Play services (and Robolectric) have no Data Layer to talk to.
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (availability != ConnectionResult.SUCCESS) return
        val request = PutDataMapRequest.create(WatchStationSync.PATH).apply {
            dataMap.putString(WatchStationSync.KEY_PAYLOAD, json)
        }.asPutDataRequest().setUrgent()
        try {
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // API_UNAVAILABLE on phones without the Wear OS companion: nothing to sync to.
            Log.d(TAG, "Wearable Data Layer unavailable (${e.statusCode})")
        } catch (e: Exception) {
            Log.w(TAG, "Watch station sync failed", e)
        }
    }
}
