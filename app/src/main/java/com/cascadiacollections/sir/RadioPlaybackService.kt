package com.cascadiacollections.sir

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.annotation.SuppressLint
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && playWhenReady) {
                            updateNotification()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            startForeground(
                                NOTIFICATION_ID,
                                buildNotification(context)
                            )
                        } else {
                            stopForeground(STOP_FOREGROUND_DETACH)
                            updateNotification()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player failed", error)
                        updateNotification(getString(R.string.radio_error))
                    }
                })
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private fun buildNotification(context: Context): Notification {
        val session = mediaSession ?: throw IllegalStateException("MediaSession is null")
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Will Radio")
            .setContentText("Live stream")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(player?.isPlaying == true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0))
            .addAction(
                if (player?.isPlaying == true)
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        context.getString(R.string.pause),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            context,
                            PlaybackStateCompat.ACTION_PAUSE
                        )
                    ).build()
                else
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_play,
                        context.getString(R.string.play),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            context,
                            PlaybackStateCompat.ACTION_PLAY
                        )
                    ).build()
            )
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(contentText: String = "Live stream") {
        val notification = buildNotification(this).apply {
            extras.putString(Notification.EXTRA_TEXT, contentText)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission missing; cannot update notification")
        }
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
        private const val TAG = "RadioPlaybackService"
        private const val STREAM_URL = "https://broadcast.shoutcheap.com/proxy/willradio/stream"
        private const val MEDIA_SESSION_ID = "will_radio_session"
        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
