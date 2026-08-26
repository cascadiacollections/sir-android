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
 * Tapping the tile opens [WearActivity], where transport controls already live.
 */
class RadioTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
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
        val recoveredSnapshotFuture = Futures.catching(
            snapshotFuture,
            Exception::class.java,
            { TileSnapshot(isPlaying = false, stationName = null) },
            MoreExecutors.directExecutor()
        )

        return Futures.transform(recoveredSnapshotFuture, ::buildTile, MoreExecutors.directExecutor())
    }

    private fun buildTile(snapshot: TileSnapshot): TileBuilders.Tile {
        val launchWearActivity = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(WearActivity::class.java.name)
                            .setPackageName(packageName)
                            .build()
                    )
                    .build()
            )
            .build()

        val layout = LayoutElementBuilders.Column.Builder()
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
                    .setClickable(launchWearActivity)
                    .build()
            )
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder().setRoot(layout).build()
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
