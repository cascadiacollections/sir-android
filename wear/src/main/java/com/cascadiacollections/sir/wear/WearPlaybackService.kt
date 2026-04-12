package com.cascadiacollections.sir.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.cascadiacollections.sir.okhttp.streaming.StreamingHttpClientFactory

class WearPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val okHttpClient = StreamingHttpClientFactory.newBuilder().build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(mapOf(
                "Icy-MetaData" to "1",
                "User-Agent" to "SIR Wear/${Build.VERSION.SDK_INT}"
            ))

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) retryCount = 0
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error (attempt ${retryCount + 1}/$MAX_RETRIES)", error)
                        if (retryCount < MAX_RETRIES) {
                            val delayMs = (2_000L * (1 shl retryCount)).coerceAtMost(30_000L)
                            handler.postDelayed({
                                retryCount++
                                prepare()
                            }, delayMs)
                        }
                    }
                })
                setMediaItem(buildMediaItem())
                prepare()
            }

        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .build()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun buildMediaItem() = MediaItem.Builder()
        .setUri(STREAM_URL)
        .setMediaId(STREAM_URL)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(getString(R.string.station_name))
                .setArtist(getString(R.string.stream_description))
                .setIsPlayable(true)
                .build()
        )
        .build()

    @OptIn(UnstableApi::class)
    private fun buildNotification() = run {
        val session = requireNotNull(mediaSession)
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, WearActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.station_name))
            .setContentText(getString(R.string.stream_description))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
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
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "WearPlaybackService"

        // Canonical source: app/.../StreamConfig.DEFAULT_STREAM_URL
        private const val STREAM_URL = "https://broadcast.shoutcheap.com/proxy/willradio/stream"
        private const val SESSION_ID = "sir_wear_session"
        private const val CHANNEL_ID = "wear_radio_playback"
        private const val NOTIFICATION_ID = 2001
        private const val MAX_RETRIES = 5
    }
}
