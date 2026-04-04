package com.cascadiacollections.sir

import android.content.ComponentName
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.guava.await

class RadioTileService : TileService() {

    private var controller: MediaController? = null

    override fun onStartListening() {
        super.onStartListening()
        val token = SessionToken(this, ComponentName(this, RadioPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                val ctrl = future.get()
                controller = ctrl
                ctrl.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = syncTile()
                    override fun onPlaybackStateChanged(state: Int) = syncTile()
                })
                syncTile()
            } catch (e: Exception) {
                Log.e(TAG, "Tile failed to connect to media session", e)
                syncTile()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStopListening() {
        super.onStopListening()
        controller?.release()
        controller = null
    }

    override fun onClick() {
        super.onClick()
        val ctrl = controller
        if (ctrl != null && ctrl.isConnected) {
            if (ctrl.isPlaying) ctrl.pause() else {
                ensureRadioServiceRunning()
                ctrl.play()
            }
        } else {
            ensureRadioServiceRunning()
            ContextCompat.startForegroundService(
                this,
                android.content.Intent(this, RadioPlaybackService::class.java).apply {
                    action = RadioPlaybackService.ACTION_PLAY
                }
            )
        }
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val playing = controller?.isPlaying == true
        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(if (playing) R.string.pause else R.string.play)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(R.string.station_name)
        }
        tile.updateTile()
    }


    companion object {
        private const val TAG = "RadioTileService"
    }
}
