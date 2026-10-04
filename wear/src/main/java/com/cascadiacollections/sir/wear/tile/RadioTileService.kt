package com.cascadiacollections.sir.wear.tile

import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.cascadiacollections.sir.wear.R
import com.cascadiacollections.sir.wear.WearActivity
import com.cascadiacollections.sir.wear.WearPlaybackService
import com.cascadiacollections.sir.wear.sync.WatchStationStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

private const val RESOURCES_VERSION = "1"

/**
 * Wear Tile showing the current station and playback state, backed by
 * [WearPlaybackService]'s [MediaController]. Tile requests are infrequent
 * (refresh-triggered, not continuous), so this builds a controller per request rather than
 * keeping a second, separately-synchronized state holder alive. `onTileRequest` returns a
 * future chained from [MediaController.Builder.buildAsync] instead of blocking on it, so the
 * calling thread is never held for the connection to complete.
 *
 * Tapping the tile opens [WearActivity], where transport controls already live. Below it,
 * "Play <station>" plays the station last played on the phone (synced by
 * `StationSyncListenerService`), as ShoutKit's watch app offers "Play Last".
 */
class RadioTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val token = SessionToken(this, ComponentName(this, WearPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, token).buildAsync()

        val snapshotFuture: ListenableFuture<TileSnapshot> = Futures.transform(
            controllerFuture,
            { controller ->
                TileSnapshot(
                    isPlaying = controller.isPlaying,
                    stationName = controller.mediaMetadata.title?.toString()
                ).also { controller.release() }
            },
            MoreExecutors.directExecutor()
        )
        val recoveredSnapshotFuture: ListenableFuture<TileSnapshot> = Futures.catching(
            snapshotFuture,
            Exception::class.java,
            { TileSnapshot(isPlaying = false, stationName = null) },
            MoreExecutors.directExecutor()
        )

        return Futures.transform(
            recoveredSnapshotFuture,
            { snapshot ->
                val last = WatchStationStore.from(this).load().last
                buildTile(snapshot, last?.name?.takeIf { it.isNotBlank() })
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun launchWearActivity(id: String, playLast: Boolean): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setClassName(WearActivity::class.java.name)
            .setPackageName(packageName)
        if (playLast) {
            activity.addKeyToExtraMapping(
                WearActivity.EXTRA_PLAY_LAST,
                ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build()
            )
        }
        return ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
            .build()
    }

    private fun buildTile(snapshot: TileSnapshot, lastStationName: String?): TileBuilders.Tile {
        val nowPlaying = LayoutElementBuilders.Column.Builder()
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(snapshot.stationName ?: getString(R.string.station_name))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(4f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(
                        getString(if (snapshot.isPlaying) R.string.live else R.string.play)
                    )
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(launchWearActivity("open_app", playLast = false))
                    .build()
            )
            .build()

        val layout = LayoutElementBuilders.Column.Builder().addContent(nowPlaying)
        // With nothing synced from the phone yet there is no "last" to offer.
        if (lastStationName != null) {
            layout
                .addContent(
                    LayoutElementBuilders.Spacer.Builder()
                        .setHeight(DimensionBuilders.dp(12f))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(getString(R.string.play_station, lastStationName))
                        .setMaxLines(1)
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setClickable(launchWearActivity("play_last", playLast = true))
                                .build()
                        )
                        .build()
                )
        }

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder().setRoot(layout.build()).build()
                    )
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = Futures.immediateFuture(
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    )

    private data class TileSnapshot(val isPlaying: Boolean, val stationName: String?)
}
