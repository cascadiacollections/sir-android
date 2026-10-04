package com.cascadiacollections.sir.wear.sync

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.cascadiacollections.sir.core.model.WatchStationSync
import com.cascadiacollections.sir.wear.complication.PlayLastComplicationService
import com.cascadiacollections.sir.wear.tile.RadioTileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

private const val TAG = "StationSyncListener"

/**
 * Receives the phone's last-played station and recents ([WatchStationSync.PATH]) and
 * keeps them in [WatchStationStore], then asks the tile and the "Play last" complication
 * to re-render. Woken by the Data Layer, so the watch app need not be running.
 *
 * The Data Layer only delivers between apps sharing an applicationId and signing key,
 * which is why `:wear` uses the phone's applicationId.
 */
class StationSyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val store = WatchStationStore.from(this)
        var changed = false
        dataEvents.forEach { event ->
            if (event.dataItem.uri.path != WatchStationSync.PATH) return@forEach
            when (event.type) {
                DataEvent.TYPE_CHANGED -> {
                    val raw = DataMapItem.fromDataItem(event.dataItem).dataMap
                        .getString(WatchStationSync.KEY_PAYLOAD)
                    store.save(raw)
                    changed = true
                }

                DataEvent.TYPE_DELETED -> {
                    store.clear()
                    changed = true
                }
            }
        }
        if (changed) refreshSurfaces(this)
    }

    companion object {

        private val stationsUri: Uri = Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .path(WatchStationSync.PATH)
            .build()

        /**
         * Pulls the phone's current item directly. The listener only hears *changes*, so a
         * watch app installed (or data cleared) after the phone last wrote would otherwise
         * show nothing until the next station change. Best effort: no Play services, no
         * paired phone or no item yet all leave the store as it was.
         */
        suspend fun fetchLatest(context: Context) {
            try {
                val buffer = Wearable.getDataClient(context).getDataItems(stationsUri).await()
                try {
                    val item = buffer.firstOrNull() ?: return
                    val raw = DataMapItem.fromDataItem(item).dataMap.getString(WatchStationSync.KEY_PAYLOAD)
                    WatchStationStore.from(context).save(raw)
                    refreshSurfaces(context)
                } finally {
                    buffer.release()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "No synced stations available", e)
            }
        }

        fun refreshSurfaces(context: Context) {
            runCatching {
                TileService.getUpdater(context).requestUpdate(RadioTileService::class.java)
            }
            runCatching {
                ComplicationDataSourceUpdateRequester.create(
                    context,
                    ComponentName(context, PlayLastComplicationService::class.java)
                ).requestUpdateAll()
            }
        }
    }
}
