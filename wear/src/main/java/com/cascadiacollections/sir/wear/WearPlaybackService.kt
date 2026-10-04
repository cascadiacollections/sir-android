package com.cascadiacollections.sir.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.StreamConfig
import com.cascadiacollections.sir.notificationcolors.NotificationAccentColor
import com.cascadiacollections.sir.okhttp.streaming.StreamingHttpClientFactory
import com.cascadiacollections.sir.wear.sync.WatchStationStore
import kotlinx.serialization.json.Json

private const val TAG = "WearPlaybackService"
private const val STREAM_URL = StreamConfig.DEFAULT_STREAM_URL
private const val SESSION_ID = "sir_wear_session"
private const val CHANNEL_ID = "wear_radio_playback"
private const val NOTIFICATION_ID = 2001
private const val MAX_RETRIES = 5

/**
 * Standalone playback on the watch. Plays the SIR stream by default; [ACTION_PLAY_STATION]
 * switches to any station (the Recent Stations list), [ACTION_PLAY_LAST] to the station
 * last played on the phone (the "Play last" complication and tile), and [ACTION_STOP]
 * stops and leaves the foreground.
 */
class WearPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val okHttpClient = StreamingHttpClientFactory.newBuilder().build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "Icy-MetaData" to "1",
                    "User-Agent" to "SIR Wear/${Build.VERSION.SDK_INT}"
                )
            )

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
                setMediaItem(mediaItemFor(defaultStation()))
                prepare()
            }

        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .build()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                return START_NOT_STICKY
            }

            ACTION_PLAY_STATION -> {
                enterForeground()
                val station = intent.getStringExtra(EXTRA_STATION)?.let(::decodeStation)
                play(station?.takeIf { it.isPlayable } ?: defaultStation())
            }

            ACTION_PLAY_LAST -> {
                enterForeground()
                play(WatchStationStore.from(this).load().last ?: defaultStation())
            }

            // Started by startForegroundService() to resume: honour that contract again,
            // since a previous Stop may have left the foreground.
            null -> enterForeground()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun defaultStation(): Station = WearStations.default(getString(R.string.station_name), STREAM_URL)

    private fun mediaItemFor(station: Station): MediaItem =
        WearStations.mediaItem(station, getString(R.string.stream_description))

    private fun play(station: Station) {
        val player = mediaSession?.player ?: return
        handler.removeCallbacksAndMessages(null)
        retryCount = 0
        if (player.currentMediaItem?.mediaId != mediaItemFor(station).mediaId) {
            player.setMediaItem(mediaItemFor(station))
        }
        player.prepare()
        player.play()
    }

    private fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)
        mediaSession?.player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterForeground() {
        if (mediaSession == null) return
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    @OptIn(UnstableApi::class)
    private fun buildNotification() = run {
        val session = requireNotNull(mediaSession)
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.station_name))
            .setContentText(getString(R.string.stream_description))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
        NotificationAccentColor.applyTo(builder)
        builder.build()
    }

    companion object {
        const val ACTION_PLAY_STATION = "com.cascadiacollections.sir.wear.action.PLAY_STATION"
        const val ACTION_PLAY_LAST = "com.cascadiacollections.sir.wear.action.PLAY_LAST"
        const val ACTION_STOP = "com.cascadiacollections.sir.wear.action.STOP"
        const val EXTRA_STATION = "com.cascadiacollections.sir.wear.extra.STATION"

        private val json = Json { ignoreUnknownKeys = true }

        fun playStationIntent(context: Context, station: Station): Intent =
            Intent(context, WearPlaybackService::class.java)
                .setAction(ACTION_PLAY_STATION)
                .putExtra(EXTRA_STATION, json.encodeToString(Station.serializer(), station))

        fun playLastIntent(context: Context): Intent =
            Intent(context, WearPlaybackService::class.java).setAction(ACTION_PLAY_LAST)

        fun stopIntent(context: Context): Intent =
            Intent(context, WearPlaybackService::class.java).setAction(ACTION_STOP)

        internal fun decodeStation(raw: String): Station? =
            runCatching { json.decodeFromString(Station.serializer(), raw) }.getOrNull()
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
}
