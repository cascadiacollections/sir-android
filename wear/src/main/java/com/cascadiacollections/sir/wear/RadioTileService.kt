package com.cascadiacollections.sir.wear

import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future

private const val ID_TOGGLE_PLAYBACK = "toggle_playback"
private const val ID_ICON_PLAY = "ic_play"
private const val ID_ICON_PAUSE = "ic_pause"
private const val RESOURCES_VERSION = "1"

/** Quick-glance Wear Tile mirroring the phone's Quick Settings [com.cascadiacollections.sir.RadioTileService]. */
class RadioTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var controller: MediaController? = null

    override fun onDestroy() {
        super.onDestroy()
        controller?.release()
        controller = null
        serviceScope.cancel()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = serviceScope.future {
        val isPlaying = connectAndMaybeToggle(requestParams)
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                Timeline.Builder()
                    .addTimelineEntry(
                        TimelineEntry.Builder()
                            .setLayout(Layout.Builder().setRoot(tileLayout(isPlaying)).build())
                            .build()
                    )
                    .build()
            )
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = serviceScope.future {
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(ID_ICON_PLAY, imageResource(R.drawable.ic_play))
            .addIdToImageMapping(ID_ICON_PAUSE, imageResource(R.drawable.ic_pause))
            .build()
    }

    private fun imageResource(resId: Int): ImageResource =
        ImageResource.Builder()
            .setAndroidResourceByResId(
                AndroidImageResourceByResId.Builder().setResourceId(resId).build()
            )
            .build()

    /**
     * Connects to the shared playback session, toggling playback if this request was triggered
     * by a tap on the tile, and returns the resulting play state.
     */
    private suspend fun connectAndMaybeToggle(requestParams: RequestBuilders.TileRequest): Boolean {
        val ctrl = connectedController() ?: return false
        val tapped = requestParams.currentState?.lastClickableId == ID_TOGGLE_PLAYBACK
        if (tapped) {
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                ContextCompat.startForegroundService(this, Intent(this, WearPlaybackService::class.java))
                ctrl.play()
            }
        }
        return ctrl.isPlaying
    }

    /**
     * Returns the cached [MediaController], connecting (or reconnecting, if the cached
     * connection dropped) on demand. Reused across [onTileRequest] calls — a Tile can be
     * refreshed by the system on every periodic refresh plus every tap, so rebuilding the
     * binder connection from scratch each time would be wasteful and could race the actual
     * toggle command against a fresh connection's initial state sync.
     */
    private suspend fun connectedController(): MediaController? {
        controller?.takeIf { it.isConnected }?.let { return it }
        val token = SessionToken(this, ComponentName(this, WearPlaybackService::class.java))
        return try {
            MediaController.Builder(this, token).buildAsync().await().also { controller = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun tileLayout(isPlaying: Boolean): LayoutElementBuilders.LayoutElement =
        Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(
                Column.Builder()
                    .setModifiers(
                        Modifiers.Builder()
                            .setClickable(
                                Clickable.Builder()
                                    .setId(ID_TOGGLE_PLAYBACK)
                                    .setOnClick(ActionBuilders.LoadAction.Builder().build())
                                    .build()
                            )
                            .setPadding(Padding.Builder().setAll(dp(8f)).build())
                            .build()
                    )
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Image.Builder()
                            .setResourceId(if (isPlaying) ID_ICON_PAUSE else ID_ICON_PLAY)
                            .setWidth(dp(48f))
                            .setHeight(dp(48f))
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText(getString(R.string.station_name))
                            .build()
                    )
                    .build()
            )
            .build()
}
