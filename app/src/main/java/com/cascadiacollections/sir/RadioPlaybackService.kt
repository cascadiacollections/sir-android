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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

class RadioPlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
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

        // Load settings asynchronously
        serviceScope.launch {
            // Apply saved stream quality (before custom URL override so debug URL wins)
            val savedQuality = settingsRepository.streamQuality.first()
            if (savedQuality != StreamQuality.HIGH) {
                currentStreamUrl = savedQuality.url
            }
            if (BuildConfig.DEBUG) {
                settingsRepository.customStreamUrl.first()?.let { customUrl ->
                    currentStreamUrl = customUrl
                    Log.d(TAG, "Using custom stream URL: $customUrl")
                }
            }
            // Re-set media item if URL changed
            if (currentStreamUrl != DEFAULT_STREAM_URL) {
                player?.setMediaItem(buildMediaItem())
                player?.prepare()
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
                return cache[hostname]
                    ?.takeIf { now - it.second < ttlMs }
                    ?.first
                    ?: Dns.SYSTEM.lookup(hostname)
                        // Prefer IPv4 for faster connection on mobile networks (false sorts before true)
                        .sortedBy { it !is Inet4Address }
                        .also { cache[hostname] = it to now }
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
                buildMap {
                    put("Icy-MetaData", "1")  // Request ICY metadata
                    put("User-Agent", "SIR Android/${Build.VERSION.SDK_INT}")
                }
            )

        // Bandwidth meter for adaptive streaming (though this stream is fixed bitrate)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setResetOnNetworkTypeChange(true)
            .build()

        // Media source factory with OkHttp data source
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        // Create optimized ExoPlayer
        val exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(SEEK_BACK_INCREMENT_MS)
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
        player = exoPlayer

        exoPlayer.setMediaItem(buildMediaItem())

        // Create media library session before adding listeners (to avoid null pointer in callbacks)
        mediaSession = MediaLibrarySession.Builder(context, exoPlayer, object : MediaLibrarySession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // Allow play/pause/seek-back; disable other seeking for live radio
                    val availableCommands =
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
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

                // Android Auto browsing: single root → single playable stream item
                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: MediaLibraryService.LibraryParams?
                ): ListenableFuture<LibraryResult<MediaItem>> =
                    Futures.immediateFuture(
                        LibraryResult.ofItem(
                            MediaItem.Builder()
                                .setMediaId(BROWSE_ROOT_ID)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setTitle(getString(R.string.station_name))
                                        .build()
                                )
                                .build(),
                            params
                        )
                    )

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: MediaLibraryService.LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
                    if (parentId == BROWSE_ROOT_ID) {
                        Futures.immediateFuture(
                            LibraryResult.ofItemList(
                                ImmutableList.of(buildMediaItem()),
                                params
                            )
                        )
                    } else {
                        Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                    }
            })
            .setId(MEDIA_SESSION_ID)
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
                    currentArtist = artist?.takeIf { it != STREAM_STATIC_ARTIST }
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
                // BLUETOOTH_CONNECT permission required on API 31+; skip action if not granted
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(
                        this@RadioPlaybackService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                }
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

            ACTION_SEEK_BACK -> {
                player?.seekBack()
            }

            ACTION_SET_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_SLEEP_TIMER_MINUTES, 0)
                setSleepTimer(minutes)
            }

            ACTION_SET_EQUALIZER -> {
                val presetOrdinal = intent.getIntExtra(EXTRA_EQUALIZER_PRESET, 0)
                applyEqualizerPreset(EqualizerPreset.fromOrdinal(presetOrdinal))
            }

            ACTION_SET_STREAM_QUALITY -> {
                val qualityOrdinal = intent.getIntExtra(EXTRA_STREAM_QUALITY, 0)
                val quality = StreamQuality.fromOrdinal(qualityOrdinal)
                applyStreamQuality(quality)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
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
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
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
        val seekBackIntent = PendingIntent.getService(
            context,
            3,
            Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_SEEK_BACK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(currentTrackTitle ?: currentStation ?: getString(R.string.station_name))
            .setContentText(currentArtist ?: getString(R.string.stream_description))
            .setSubText(
                if (currentTrackTitle != null) currentStation ?: getString(R.string.station_name) else null
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setDeleteIntent(stopIntent)
            .setOngoing(player?.isPlaying == true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1)
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
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_rew,
                    context.getString(R.string.seek_back_30),
                    seekBackIntent
                ).build()
            )
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotificationSafe(contentText: String = getString(R.string.stream_description)) {
        if (mediaSession == null) return
        updateNotification(contentText)
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(contentText: String = getString(R.string.stream_description)) {
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
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildMediaItem(): MediaItem = MediaItem.Builder()
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
                .setTitle(getString(R.string.station_name))
                .setArtist(getString(R.string.stream_description))
                .setIsPlayable(true)
                .build()
        )
        .build()

    private fun resumeIfPausedByNoisy() {
        if (!pausedByNoisy) return
        pausedByNoisy = false
        player?.play()
    }

    @SuppressLint("WakelockTimeout")
    private fun initializeLocks() {
        // Wake lock to prevent CPU sleep during audio playback
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SIR::PlaybackWakeLock"
        )

        // WiFi lock to prevent WiFi from going into low-power mode.
        // WIFI_MODE_FULL_LOW_LATENCY (API 29+) reduces buffering on Pixel 10's WiFi 7 chipset;
        // fall back to WIFI_MODE_FULL_HIGH_PERF on older devices.
        val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
        wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "SIR::PlaybackWifiLock"
            )
        } else {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "SIR::PlaybackWifiLock"
            )
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        wakeLock?.takeUnless { it.isHeld }?.run {
            acquire()
            Log.d(TAG, "Wake lock acquired")
        }
        wifiLock?.takeUnless { it.isHeld }?.run {
            acquire()
            Log.d(TAG, "WiFi lock acquired")
        }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.run {
            release()
            Log.d(TAG, "Wake lock released")
        }
        wifiLock?.takeIf { it.isHeld }?.run {
            release()
            Log.d(TAG, "WiFi lock released")
        }
    }

    // Sleep Timer methods
    private fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()

        if (minutes <= 0) {
            Log.d(TAG, "Sleep timer disabled")
            return
        }

        val runnable = Runnable {
            Log.d(TAG, "Sleep timer triggered - stopping playback")
            serviceScope.launch { settingsRepository.setSleepTimerFiresAt(0L) }
            player?.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        sleepTimerRunnable = runnable

        val delayMs = minutes * 60 * 1000L
        sleepTimerHandler.postDelayed(runnable, delayMs)
        serviceScope.launch { settingsRepository.setSleepTimerFiresAt(System.currentTimeMillis() + delayMs) }
        Log.d(TAG, "Sleep timer set for $minutes minutes")
    }

    private fun cancelSleepTimer() {
        sleepTimerRunnable?.also {
            sleepTimerHandler.removeCallbacks(it)
            Log.d(TAG, "Sleep timer cancelled")
        }
        sleepTimerRunnable = null
        serviceScope.launch { settingsRepository.setSleepTimerFiresAt(0L) }
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

            // Apply preset using unified curve function
            val levels = when (preset) {
                EqualizerPreset.NORMAL -> List(bandCount) { 0.toShort() }
                EqualizerPreset.BASS_BOOST -> calculateEqualizerLevels(
                    bandCount,
                    minLevel,
                    maxLevel,
                    range
                ) { pos ->
                    (1 - pos) * 0.6f
                }

                EqualizerPreset.VOCAL -> calculateEqualizerLevels(
                    bandCount,
                    minLevel,
                    maxLevel,
                    range
                ) { pos ->
                    when {
                        pos < 0.3f -> 0.1f  // Cut bass
                        pos < 0.7f -> 0.7f  // Boost mids
                        else -> 0.4f        // Slight boost highs
                    }
                }

                EqualizerPreset.TREBLE -> calculateEqualizerLevels(
                    bandCount,
                    minLevel,
                    maxLevel,
                    range
                ) { pos ->
                    pos * 0.6f
                }
            }

            levels.forEachIndexed { band, level ->
                eq.setBandLevel(band.toShort(), level)
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

    private fun applyStreamQuality(quality: StreamQuality) {
        val newUrl = quality.url
        if (newUrl == currentStreamUrl) return
        currentStreamUrl = newUrl
        val wasPlaying = player?.isPlaying == true
        player?.stop()
        player?.setMediaItem(buildMediaItem())
        player?.prepare()
        if (wasPlaying) player?.play()
        serviceScope.launch { settingsRepository.setStreamQuality(quality) }
        Log.d(TAG, "Stream quality changed to ${quality.label}: $newUrl")
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

        // Stream configuration
        private const val DEFAULT_STREAM_URL =
            "https://broadcast.shoutcheap.com/proxy/willradio/stream"
        private const val DEFAULT_STATION_NAME = "SIR"
        private const val DEFAULT_STREAM_DESCRIPTION = "Live stream"

        // Stream's static metadata values (not real track info)
        private const val STREAM_STATIC_TITLE = "Will Radio Stream"
        private const val STREAM_STATIC_ARTIST = "Live Internet Radio"

        // Media session & notification
        private const val MEDIA_SESSION_ID = "will_radio_session"
        private const val BROWSE_ROOT_ID = "sir_root"
        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SEEK_BACK_INCREMENT_MS = 30_000L

        // Intent actions
        private const val ACTION_STOP = "com.cascadiacollections.sir.action.STOP"
        const val ACTION_PLAY = "com.cascadiacollections.sir.action.PLAY"
        private const val ACTION_PAUSE = "com.cascadiacollections.sir.action.PAUSE"
        const val ACTION_SEEK_BACK = "com.cascadiacollections.sir.action.SEEK_BACK"
        const val ACTION_SET_SLEEP_TIMER = "com.cascadiacollections.sir.action.SET_SLEEP_TIMER"
        const val ACTION_SET_EQUALIZER = "com.cascadiacollections.sir.action.SET_EQUALIZER"

        // Intent extras
        const val EXTRA_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
        const val EXTRA_EQUALIZER_PRESET = "equalizer_preset"
        const val ACTION_SET_STREAM_QUALITY = "com.cascadiacollections.sir.action.SET_STREAM_QUALITY"
        const val EXTRA_STREAM_QUALITY = "stream_quality_ordinal"
    }
}

/**
 * Calculate equalizer band levels using a curve function.
 * @param curve Function mapping band position (0.0..1.0) to level multiplier (0.0..1.0)
 */
internal fun calculateEqualizerLevels(
    bandCount: Int,
    minLevel: Short,
    maxLevel: Short,
    range: Int,
    curve: (Float) -> Float
): List<Short> = List(bandCount) { band ->
    val position = band.toFloat() / (bandCount - 1).coerceAtLeast(1)
    (minLevel + range * curve(position)).toInt().toShort().coerceIn(minLevel, maxLevel)
}
