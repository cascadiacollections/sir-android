package com.cascadiacollections.sir

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class RadioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var audioManager: AudioManager
    private var isNoisyReceiverRegistered = false
    private var isRouteReceiverRegistered = false
    private var pausedByNoisy = false

    // Locks to keep device active during playback
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Current stream metadata from ICY headers
    private var currentTrackTitle: String? = null
    private var currentArtist: String? = null
    private var currentStation: String? = null

    // Sleep timer
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    // Equalizer
    private var equalizer: Equalizer? = null
    private var currentEqualizerPreset: EqualizerPreset = EqualizerPreset.NORMAL

    // Settings and coroutine scope
    private lateinit var settingsRepository: SettingsRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current stream URL (may be custom in debug builds)
    private var currentStreamUrl: String = DEFAULT_STREAM_URL

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action && player?.isPlaying == true) {
                pausedByNoisy = true
                player?.pause()
            }
        }
    }
    private val audioRouteRestoredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    if (state == 1) resumeIfPausedByNoisy()
                }

                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED
                    )
                    if (state == BluetoothProfile.STATE_CONNECTED) resumeIfPausedByNoisy()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        val context = this

        // Initialize settings repository
        settingsRepository = SettingsRepository(this)

        // Load custom stream URL in debug builds
        serviceScope.launch {
            if (BuildConfig.DEBUG) {
                settingsRepository.customStreamUrl.first()?.let { customUrl ->
                    currentStreamUrl = customUrl
                    Log.d(TAG, "Using custom stream URL: $customUrl")
                }
            }
            // Load and apply equalizer preset
            currentEqualizerPreset = settingsRepository.equalizerPreset.first()
        }

        // Initialize wake locks to prevent device sleep during playback
        initializeLocks()

        // Optimized load control for 64kbps live radio stream
        // Stream: 64kbps = 8KB/s, so 10 seconds = 80KB buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,      // 15 sec min buffer (120KB at 64kbps)
                /* maxBufferMs */ 60_000,      // 60 sec max buffer (480KB at 64kbps)
                /* bufferForPlaybackMs */ 2_500,    // Start playback after 2.5 sec buffer
                /* bufferForPlaybackAfterRebufferMs */ 5_000  // 5 sec buffer after rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)  // Prioritize low latency
            .build()

        // OkHttp client optimized for live audio streaming
        // - Connection pooling for instant reconnects on network switches
        // - HTTP/2 with HTTP/1.1 fallback for maximum compatibility
        // - Keep-alive for persistent streaming connection
        // - DNS caching for faster reconnects
        val connectionPool = ConnectionPool(
            maxIdleConnections = 2,        // Keep 2 connections warm for fast reconnects
            keepAliveDuration = 5,         // 5 minute keep-alive
            timeUnit = TimeUnit.MINUTES
        )

        // DNS caching to avoid repeated lookups on reconnect
        val cachingDns = object : Dns {
            private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
            private val ttlMs = 5 * 60 * 1000L  // 5 minute TTL

            override fun lookup(hostname: String): List<InetAddress> {
                val now = System.currentTimeMillis()
                val cached = cache[hostname]
                if (cached != null && now - cached.second < ttlMs) {
                    return cached.first
                }
                val addresses = Dns.SYSTEM.lookup(hostname)
                    // Prefer IPv4 for faster connection on mobile networks
                    .sortedBy { if (it is Inet4Address) 0 else 1 }
                cache[hostname] = addresses to now
                return addresses
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dns(cachingDns)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))  // Prefer HTTP/2
            .connectTimeout(10, TimeUnit.SECONDS)   // Faster connect timeout
            .readTimeout(30, TimeUnit.SECONDS)      // Longer read timeout for streaming
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)       // No overall timeout for streaming
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

        // OkHttp data source for better HTTP performance (HTTP/2, connection reuse)
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "Icy-MetaData" to "1",  // Request ICY metadata
                    "User-Agent" to "SIR Android/${Build.VERSION.SDK_INT}"
                )
            )

        // Bandwidth meter for adaptive streaming (though this stream is fixed bitrate)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setResetOnNetworkTypeChange(true)
            .build()

        // Media source factory with OkHttp data source
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        // Create optimized ExoPlayer
        player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true  // Handle audio focus automatically
            )
            .setHandleAudioBecomingNoisy(false)  // We handle this manually for more control
            .setWakeMode(C.WAKE_MODE_NETWORK)    // Keep CPU and network active
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF  // Live stream doesn't repeat
                playWhenReady = false  // Don't auto-play on creation
            }

        val mediaItem = MediaItem.Builder()
            .setUri(currentStreamUrl)
            .setMediaId(currentStreamUrl)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.02f)  // Slight speedup to catch up if behind
                    .setMinPlaybackSpeed(0.98f)  // Slight slowdown if too far ahead
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(DEFAULT_STATION_NAME)
                    .setArtist(DEFAULT_STREAM_DESCRIPTION)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

        player?.setMediaItem(mediaItem)

        // Create media session before adding listeners (to avoid null pointer in callbacks)
        mediaSession = MediaSession.Builder(context, player!!)
            .setId(MEDIA_SESSION_ID)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // Only allow play/pause commands, no seeking for live radio
                    val availableCommands =
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                            .remove(Player.COMMAND_SEEK_BACK)
                            .remove(Player.COMMAND_SEEK_FORWARD)
                            .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                            .remove(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                            .remove(Player.COMMAND_SEEK_TO_NEXT)
                            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(availableCommands)
                        .build()
                }
            })
            .build()

        // Now add listeners after mediaSession is created
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player?.playWhenReady == true) {
                    updateNotificationSafe()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    acquireLocks()
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(context)
                    )
                } else {
                    releaseLocks()
                    stopForeground(STOP_FOREGROUND_DETACH)
                    updateNotificationSafe()
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // ICY metadata from internet radio stream
                val streamTitle = mediaMetadata.title?.toString()
                val artist = mediaMetadata.artist?.toString()
                val station = mediaMetadata.station?.toString()

                Log.d(TAG, "Stream metadata: title=$streamTitle, artist=$artist, station=$station")

                // Capture station name if available
                if (!station.isNullOrBlank()) {
                    currentStation = station
                }

                // Check if this is real track metadata vs stream's static defaults
                val isStaticMetadata = streamTitle == STREAM_STATIC_TITLE ||
                    streamTitle == DEFAULT_STATION_NAME ||
                    streamTitle.isNullOrBlank()

                if (!isStaticMetadata) {
                    // Real track info - could be "Artist - Title" format
                    currentTrackTitle = streamTitle
                    currentArtist = if (artist != STREAM_STATIC_ARTIST) artist else null
                    updateNotificationSafe()
                } else if (!station.isNullOrBlank() && currentStation != station) {
                    // Station name changed, update notification
                    updateNotificationSafe()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player failed", error)
                updateNotificationSafe(getString(R.string.radio_error))
            }
        })

        // Now prepare the player
        player?.prepare()

        // Initialize equalizer with the player's audio session
        initializeEqualizer()

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(context)
        )

        ContextCompat.registerReceiver(
            this,
            audioBecomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isNoisyReceiverRegistered = true
        ContextCompat.registerReceiver(
            this,
            audioRouteRestoredReceiver,
            IntentFilter().apply {
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRouteReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                pausedByNoisy = false
                cancelSleepTimer()
                player?.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PLAY -> {
                player?.play()
            }

            ACTION_PAUSE -> {
                player?.pause()
            }

            ACTION_SET_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_SLEEP_TIMER_MINUTES, 0)
                setSleepTimer(minutes)
            }

            ACTION_SET_EQUALIZER -> {
                val presetOrdinal = intent.getIntExtra(EXTRA_EQUALIZER_PRESET, 0)
                applyEqualizerPreset(EqualizerPreset.fromOrdinal(presetOrdinal))
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // Cancel sleep timer
        cancelSleepTimer()

        // Release equalizer
        releaseEqualizer()

        // Cancel coroutine scope
        serviceScope.cancel()

        releaseLocks()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        player?.run {
            release()
            player = null
        }
        if (isNoisyReceiverRegistered) {
            unregisterReceiver(audioBecomingNoisyReceiver)
            isNoisyReceiverRegistered = false
        }
        if (isRouteReceiverRegistered) {
            unregisterReceiver(audioRouteRestoredReceiver)
            isRouteReceiverRegistered = false
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
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(currentTrackTitle ?: currentStation ?: DEFAULT_STATION_NAME)
            .setContentText(currentArtist ?: DEFAULT_STREAM_DESCRIPTION)
            .setSubText(
                if (currentTrackTitle != null) currentStation ?: DEFAULT_STATION_NAME else null
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setDeleteIntent(stopIntent)
            .setOngoing(player?.isPlaying == true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0)
            )
            .addAction(
                if (player?.isPlaying == true)
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        context.getString(R.string.pause),
                        pauseIntent
                    ).build()
                else
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_play,
                        context.getString(R.string.play),
                        playIntent
                    ).build()
            )
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotificationSafe(contentText: String = DEFAULT_STREAM_DESCRIPTION) {
        if (mediaSession == null) return
        updateNotification(contentText)
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(contentText: String = DEFAULT_STREAM_DESCRIPTION) {
        val notification = buildNotification(this).apply {
            extras.putString(Notification.EXTRA_TEXT, contentText)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
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

    private fun resumeIfPausedByNoisy() {
        if (pausedByNoisy) {
            pausedByNoisy = false
            player?.play()
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun initializeLocks() {
        // Wake lock to prevent CPU sleep during audio playback
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SIR::PlaybackWakeLock"
        )

        // WiFi lock to prevent WiFi from going into low-power mode
        val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "SIR::PlaybackWifiLock"
        )
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire()
                Log.d(TAG, "Wake lock acquired")
            }
        }
        wifiLock?.let {
            if (!it.isHeld) {
                it.acquire()
                Log.d(TAG, "WiFi lock acquired")
            }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WiFi lock released")
            }
        }
    }

    // Sleep Timer methods
    private fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()

        if (minutes <= 0) {
            Log.d(TAG, "Sleep timer disabled")
            return
        }

        sleepTimerRunnable = Runnable {
            Log.d(TAG, "Sleep timer triggered - stopping playback")
            player?.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        val delayMs = minutes * 60 * 1000L
        sleepTimerHandler.postDelayed(sleepTimerRunnable!!, delayMs)
        Log.d(TAG, "Sleep timer set for $minutes minutes")
    }

    private fun cancelSleepTimer() {
        sleepTimerRunnable?.let {
            sleepTimerHandler.removeCallbacks(it)
            sleepTimerRunnable = null
            Log.d(TAG, "Sleep timer cancelled")
        }
    }

    // Equalizer methods
    @OptIn(UnstableApi::class)
    private fun initializeEqualizer() {
        try {
            val audioSessionId = player?.audioSessionId ?: return
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
                Log.w(TAG, "Audio session ID not set, skipping equalizer init")
                return
            }

            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            applyEqualizerPreset(currentEqualizerPreset)
            Log.d(TAG, "Equalizer initialized with session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize equalizer", e)
        }
    }

    private fun applyEqualizerPreset(preset: EqualizerPreset) {
        currentEqualizerPreset = preset
        val eq = equalizer ?: return

        try {
            val bandCount = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]
            val range = maxLevel - minLevel

            // Apply preset based on type
            when (preset) {
                EqualizerPreset.NORMAL -> {
                    // Flat response
                    for (band in 0 until bandCount) {
                        eq.setBandLevel(band.toShort(), 0)
                    }
                }

                EqualizerPreset.BASS_BOOST -> {
                    // Boost low frequencies, slight cut on highs
                    val levels = calculateBassBoostLevels(bandCount, minLevel, maxLevel, range)
                    for (band in 0 until bandCount) {
                        eq.setBandLevel(band.toShort(), levels[band])
                    }
                }

                EqualizerPreset.VOCAL -> {
                    // Boost mids for voice clarity, cut bass
                    val levels = calculateVocalLevels(bandCount, minLevel, maxLevel, range)
                    for (band in 0 until bandCount) {
                        eq.setBandLevel(band.toShort(), levels[band])
                    }
                }

                EqualizerPreset.TREBLE -> {
                    // Boost high frequencies
                    val levels = calculateTrebleLevels(bandCount, minLevel, maxLevel, range)
                    for (band in 0 until bandCount) {
                        eq.setBandLevel(band.toShort(), levels[band])
                    }
                }
            }

            // Persist preference
            serviceScope.launch {
                settingsRepository.setEqualizerPreset(preset)
            }

            Log.d(TAG, "Applied equalizer preset: ${preset.label}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply equalizer preset", e)
        }
    }

    private fun calculateBassBoostLevels(
        bandCount: Int,
        minLevel: Short,
        maxLevel: Short,
        range: Int
    ): List<Short> {
        // Bass boost: high on low bands, tapering to flat/slight cut on highs
        return List(bandCount) { band ->
            val position = band.toFloat() / (bandCount - 1)
            val boost = ((1 - position) * 0.6f * range + minLevel).toInt().toShort()
            boost.coerceIn(minLevel, maxLevel)
        }
    }

    private fun calculateVocalLevels(
        bandCount: Int,
        minLevel: Short,
        maxLevel: Short,
        range: Int
    ): List<Short> {
        // Vocal: cut bass, boost mids, slight cut on highs
        return List(bandCount) { band ->
            val position = band.toFloat() / (bandCount - 1)
            val level = when {
                position < 0.3f -> (minLevel + range * 0.1f).toInt() // Cut bass
                position < 0.7f -> (minLevel + range * 0.7f).toInt() // Boost mids
                else -> (minLevel + range * 0.4f).toInt() // Slight boost highs
            }.toShort()
            level.coerceIn(minLevel, maxLevel)
        }
    }

    private fun calculateTrebleLevels(
        bandCount: Int,
        minLevel: Short,
        maxLevel: Short,
        range: Int
    ): List<Short> {
        // Treble boost: flat on lows, boost highs
        return List(bandCount) { band ->
            val position = band.toFloat() / (bandCount - 1)
            val boost = (position * 0.6f * range + minLevel).toInt().toShort()
            boost.coerceIn(minLevel, maxLevel)
        }
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.release()
            equalizer = null
            Log.d(TAG, "Equalizer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing equalizer", e)
        }
    }

    companion object {
        private const val TAG = "RadioPlaybackService"
        private const val DEFAULT_STREAM_URL =
            "https://broadcast.shoutcheap.com/proxy/willradio/stream"
        private const val MEDIA_SESSION_ID = "will_radio_session"
        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.cascadiacollections.sir.action.STOP"
        private const val ACTION_PLAY = "com.cascadiacollections.sir.action.PLAY"
        private const val ACTION_PAUSE = "com.cascadiacollections.sir.action.PAUSE"
        const val ACTION_SET_SLEEP_TIMER = "com.cascadiacollections.sir.action.SET_SLEEP_TIMER"
        const val ACTION_SET_EQUALIZER = "com.cascadiacollections.sir.action.SET_EQUALIZER"
        const val EXTRA_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
        const val EXTRA_EQUALIZER_PRESET = "equalizer_preset"
        private const val DEFAULT_STATION_NAME = "SIR"
        private const val DEFAULT_STREAM_DESCRIPTION = "Live stream"

        // Stream's static metadata values (not real track info)
        private const val STREAM_STATIC_TITLE = "Will Radio Stream"
        private const val STREAM_STATIC_ARTIST = "Live Internet Radio"
    }
}
