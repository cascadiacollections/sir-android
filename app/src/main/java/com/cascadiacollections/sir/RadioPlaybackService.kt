package com.cascadiacollections.sir

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper

class RadioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val context = this
        player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
            }

        val mediaItem = MediaItem.Builder()
            .setUri(STREAM_URL)
            .setMediaId(STREAM_URL)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Will Radio Stream")
                    .setArtist("Live Internet Radio")
                    .build()
            )
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
        }

        mediaSession = MediaSession.Builder(context, player!!)
            .setId(MEDIA_SESSION_ID)
            .build()

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(context)
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        player?.run {
            release()
            player = null
        }
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private fun buildNotification(context: Context): Notification {
        val session = mediaSession ?: throw IllegalStateException("MediaSession is null")
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Will Radio")
            .setContentText("Live stream")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for radio playback"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val STREAM_URL = "https://broadcast.shoutcheap.com/proxy/willradio/stream"
        private const val MEDIA_SESSION_ID = "will_radio_session"
        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
