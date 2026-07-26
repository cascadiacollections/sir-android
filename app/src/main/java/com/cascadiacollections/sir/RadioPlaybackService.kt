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
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.cascadiacollections.android.media3.timeshift.CircularByteBuffer
import com.cascadiacollections.android.media3.timeshift.PlaybackMode
import com.cascadiacollections.android.media3.timeshift.TimeShiftDataSource
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.core.playback.AudioRoutePolicy
import com.cascadiacollections.sir.core.playback.EqualizerCurves
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.PlaybackBufferConfig
import com.cascadiacollections.sir.core.playback.PlaybackLocks
import com.cascadiacollections.sir.core.playback.RawStreamMetadata
import com.cascadiacollections.sir.core.playback.RetryBackoff
import com.cascadiacollections.sir.core.playback.SleepTimerRestore
import com.cascadiacollections.sir.core.playback.StreamConfig
import com.cascadiacollections.sir.core.playback.StreamMetadata
import com.cascadiacollections.sir.core.playback.StreamMetadataResolver
import com.cascadiacollections.sir.core.playback.StreamQuality
import com.cascadiacollections.sir.core.playback.StreamSource
import com.cascadiacollections.sir.core.playback.StreamSourceResolver
import com.cascadiacollections.sir.okhttp.streaming.StreamingHttpClientFactory
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RadioPlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val audioManager: AudioManager by lazy { getSystemService(AudioManager::class.java) }
    private var isNoisyReceiverRegistered = false
    private var isRouteReceiverRegistered = false
    private val audioRoutePolicy = AudioRoutePolicy()
    private val retryBackoff = RetryBackoff()

    // Locks to keep device active during playback
    private var playbackLocks: PlaybackLocks? = null

    // Current stream metadata from ICY headers
    private val metadataResolver = StreamMetadataResolver(
        staticTitles = setOf(STREAM_STATIC_TITLE, DEFAULT_STATION_NAME),
        staticArtists = setOf(STREAM_STATIC_ARTIST),
    )
    private var streamMetadata = StreamMetadata()
    private val currentTrackTitle: String? get() = streamMetadata.trackTitle
    private val currentArtist: String? get() = streamMetadata.artist
    private val currentStation: String? get() = streamMetadata.station

    // Sleep timer
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    // Equalizer
    private var equalizer: Equalizer? = null
    private var currentEqualizerPreset: EqualizerPreset = EqualizerPreset.NORMAL

    // Settings and coroutine scope
    private val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current stream URL (may be a directory station or a debug override)
    private var currentStreamUrl: String = DEFAULT_STREAM_URL

    // Display title for the current stream; null falls back to the bundled station name
    private var currentStationTitle: String? = null

    // DVR time-shift buffer
    private val replayBuffer = CircularByteBuffer(REPLAY_BUFFER_SIZE)
    private var playbackMode: PlaybackMode = PlaybackMode.Live
    private var timeShiftDataSourceFactory: TimeShiftDataSource.Factory? = null

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY != intent?.action) return
            if (audioRoutePolicy.onBecomingNoisy(isPlaying = player?.isPlaying == true)) {
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
        val context = this

        // Load settings asynchronously
        serviceScope.launch {
            applyStreamSource(resolveStreamSource())

            // Load and apply equalizer preset
            currentEqualizerPreset = settingsRepository.equalizerPreset.first()

            // Restore sleep timer if it was active before process death
            val firesAt = settingsRepository.sleepTimerFiresAt.first()
            if (firesAt > 0L) {
                val remainingMinutes = SleepTimerRestore.remainingMinutes(
                    firesAtEpochMillis = firesAt,
                    nowEpochMillis = System.currentTimeMillis()
                )
                if (remainingMinutes != null) {
                    setSleepTimer(remainingMinutes)
                    Log.d(TAG, "Restored sleep timer: ${remainingMinutes}m remaining")
                } else {
                    // Timer already expired — clear persisted value
                    settingsRepository.setSleepTimerFiresAt(0L)
                }
            }
        }

        // React to the user picking a different station while the service is alive
        serviceScope.launch {
            settingsRepository.selectedStation
                .drop(1)
                .collect { applyStreamSource(resolveStreamSource()) }
        }

        // Initialize wake locks to prevent device sleep during playback
        playbackLocks = PlaybackLocks(this)

        val bufferConfig = PlaybackBufferConfig.LIVE_RADIO
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferConfig.minBufferMs,
                bufferConfig.maxBufferMs,
                bufferConfig.bufferForPlaybackMs,
                bufferConfig.bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(bufferConfig.prioritizeTimeOverSizeThresholds)
            .build()

        // OkHttp client optimized for live audio streaming
        val okHttpClient = StreamingHttpClientFactory.newBuilder()
            .writeTimeout(10, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        okhttp3.logging.HttpLoggingInterceptor().setLevel(
                            okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS
                        )
                    )
                }
            }
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

        // Time-shift data source wraps OkHttp for DVR-style replay
        val timeShiftFactory = TimeShiftDataSource.Factory(httpDataSourceFactory, replayBuffer)
        timeShiftDataSourceFactory = timeShiftFactory

        // Media source factory with time-shift data source
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(timeShiftFactory)

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
                    val availableSessionCommands =
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand(ACTION_SEEK_BACK, android.os.Bundle.EMPTY))
                            .add(SessionCommand(ACTION_GO_LIVE, android.os.Bundle.EMPTY))
                            .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(availableCommands)
                        .setAvailableSessionCommands(availableSessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: android.os.Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_SEEK_BACK -> {
                            if (!SEEKBACK_ENABLED) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                            val bytesToSeek = (SEEK_BACK_INCREMENT_MS / 1000 * STREAM_BYTES_PER_SEC).toInt()
                            if (!replayBuffer.canSeekBack(bytesToSeek)) {
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                            }
                            timeShiftDataSourceFactory?.lastCreated?.seekBack(bytesToSeek)
                            playbackMode = PlaybackMode.TimeShifted
                            flushPlayer()
                            updateCustomLayout()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        ACTION_GO_LIVE -> {
                            if (!SEEKBACK_ENABLED) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                            timeShiftDataSourceFactory?.lastCreated?.goLive()
                            playbackMode = PlaybackMode.Live
                            flushPlayer()
                            updateCustomLayout()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
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
                                        .setTitle(currentStationTitle ?: getString(R.string.station_name))
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
            // No initial custom layout — updateCustomLayout() adds "Replay 30s"
            // once the buffer has enough data. Calling setCustomLayout with an
            // empty list crashes the legacy PlaybackStateCompat stub.

        // Now add listeners after mediaSession is created
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retryBackoff.reset()
                    if (player?.playWhenReady == true) {
                        updateCustomLayout()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    playbackLocks?.acquire()
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(context)
                    )
                    if (SEEKBACK_ENABLED) scheduleSeekBackReveal()
                } else {
                    playbackLocks?.release()
                    stopForeground(STOP_FOREGROUND_DETACH)
                    updateNotificationSafe()
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // ICY metadata from internet radio stream
                val raw = RawStreamMetadata(
                    title = mediaMetadata.title?.toString(),
                    artist = mediaMetadata.artist?.toString(),
                    station = mediaMetadata.station?.toString(),
                )
                Log.d(TAG, "Stream metadata: $raw")

                val update = metadataResolver.resolve(streamMetadata, raw)
                streamMetadata = update.metadata
                if (update.notifyChanged) updateNotificationSafe()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error (attempt ${retryBackoff.attemptLabel})", error)
                val delayMs = retryBackoff.nextDelayMs()
                if (delayMs != null) {
                    updateNotificationSafe(getString(R.string.stream_reconnecting))
                    sleepTimerHandler.postDelayed({ player?.prepare() }, delayMs)
                } else {
                    updateNotificationSafe(getString(R.string.radio_error))
                }
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

    @OptIn(UnstableApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                audioRoutePolicy.onPlaybackStateChangedByUser()
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
                if (SEEKBACK_ENABLED) {
                    val bytesToSeek = (SEEK_BACK_INCREMENT_MS / 1000 * STREAM_BYTES_PER_SEC).toInt()
                    if (replayBuffer.canSeekBack(bytesToSeek)) {
                        timeShiftDataSourceFactory?.lastCreated?.seekBack(bytesToSeek)
                        playbackMode = PlaybackMode.TimeShifted
                        flushPlayer()
                        updateCustomLayout()
                    }
                }
            }

            ACTION_GO_LIVE -> {
                if (SEEKBACK_ENABLED) {
                    timeShiftDataSourceFactory?.lastCreated?.goLive()
                    playbackMode = PlaybackMode.Live
                    flushPlayer()
                    updateCustomLayout()
                }
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
        // Cancel pending callbacks
        seekBackRevealRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        cancelSleepTimer()

        // Release equalizer
        releaseEqualizer()

        // Clear replay buffer
        replayBuffer.clear()

        // Cancel coroutine scope
        serviceScope.cancel()

        playbackLocks?.release()
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
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Keep our custom notification (with seek-back action) instead of Media3's default
        val notification = buildNotification(this)
        if (player?.isPlaying == true || startInForegroundRequired) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildNotification(context: Context): Notification {
        val session = checkNotNull(mediaSession) { "MediaSession is null" }
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
        val seekBytes = (SEEK_BACK_INCREMENT_MS / 1000 * STREAM_BYTES_PER_SEC).toInt()
        val canSeek = replayBuffer.canSeekBack(seekBytes)

        // Action index tracking for compact view
        var actionIndex = 0
        val compactIndices = mutableListOf(actionIndex) // play/pause always compact

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(currentTrackTitle ?: currentStation ?: getString(R.string.station_name))
            .setContentText(currentArtist ?: getString(R.string.stream_description))
            .setSubText(
                currentTrackTitle?.let { currentStation ?: getString(R.string.station_name) }
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setDeleteIntent(stopIntent)
            .setOngoing(player?.isPlaying == true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

        if (SEEKBACK_ENABLED && canSeek) {
            actionIndex++
            compactIndices += actionIndex
            val seekBackIntent = PendingIntent.getService(
                context, 3,
                Intent(context, RadioPlaybackService::class.java).apply {
                    action = ACTION_SEEK_BACK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_rew,
                    context.getString(R.string.seek_back_30),
                    seekBackIntent
                ).build()
            )
        }

        if (SEEKBACK_ENABLED && playbackMode is PlaybackMode.TimeShifted) {
            actionIndex++
            compactIndices += actionIndex
            val goLiveIntent = PendingIntent.getService(
                context, 4,
                Intent(context, RadioPlaybackService::class.java).apply {
                    action = ACTION_GO_LIVE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_ff,
                    context.getString(R.string.go_live),
                    goLiveIntent
                ).build()
            )
        }

        builder.setStyle(
            MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(*compactIndices.toIntArray())
        )

        return builder.build()
    }

    /**
     * Force ExoPlayer to discard its internal decoded buffer and re-read from
     * the [CircularByteBuffer] at the current read cursor position.
     * Without this, ExoPlayer's 60s internal buffer would keep playing stale
     * audio after a seekBack or goLive cursor change.
     */
    private fun flushPlayer() {
        val p = player ?: return
        val wasPlaying = p.playWhenReady
        p.stop()
        p.prepare()
        if (wasPlaying) p.play()
    }

    private var seekBackRevealRunnable: Runnable? = null

    /** Post a delayed check to reveal "Replay 30s" once enough data is buffered. */
    private fun scheduleSeekBackReveal() {
        seekBackRevealRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        val seekBytes = (SEEK_BACK_INCREMENT_MS / 1000 * STREAM_BYTES_PER_SEC).toInt()
        if (replayBuffer.canSeekBack(seekBytes)) {
            updateCustomLayout()
            return
        }
        val runnable = Runnable { updateCustomLayout() }
        seekBackRevealRunnable = runnable
        // Check shortly after 30s of buffering; add 2s margin for network jitter
        sleepTimerHandler.postDelayed(runnable, SEEK_BACK_INCREMENT_MS + 2_000)
    }

    private fun updateCustomLayout() {
        val session = mediaSession ?: return
        val seekBytes = (SEEK_BACK_INCREMENT_MS / 1000 * STREAM_BYTES_PER_SEC).toInt()
        val canSeek = replayBuffer.canSeekBack(seekBytes)

        val buttons = mutableListOf<CommandButton>()
        if (SEEKBACK_ENABLED && canSeek) {
            buttons += CommandButton.Builder(CommandButton.ICON_SKIP_BACK_30)
                .setDisplayName(getString(R.string.seek_back_30))
                .setSessionCommand(SessionCommand(ACTION_SEEK_BACK, android.os.Bundle.EMPTY))
                .build()
        }
        if (SEEKBACK_ENABLED && playbackMode is PlaybackMode.TimeShifted) {
            buttons += CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
                .setDisplayName(getString(R.string.go_live))
                .setSessionCommand(SessionCommand(ACTION_GO_LIVE, android.os.Bundle.EMPTY))
                .build()
        }
        // Only update layout when we have buttons — empty list crashes the
        // legacy PlaybackStateCompat CustomAction builder (requires icon)
        if (buttons.isNotEmpty()) {
            session.setCustomLayout(ImmutableList.copyOf(buttons))
        }
        updateNotificationSafe()
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
                .setTitle(currentStationTitle ?: getString(R.string.station_name))
                .setArtist(getString(R.string.stream_description))
                .setIsPlayable(true)
                .build()
        )
        .build()

    private fun resumeIfPausedByNoisy() {
        if (audioRoutePolicy.onRouteRestored()) player?.play()
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
            val levels = EqualizerCurves.levelsFor(
                preset = preset,
                bandCount = eq.numberOfBands.toInt(),
                minLevel = eq.bandLevelRange[0],
                maxLevel = eq.bandLevelRange[1]
            )

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

    /**
     * Resolves the stream to play from the persisted settings. The debug override is
     * only consulted in debug builds so a release build can never be pointed at an
     * arbitrary URL by stale preferences.
     */
    private suspend fun resolveStreamSource(): StreamSource = StreamSourceResolver.resolve(
        debugOverrideUrl = if (BuildConfig.DEBUG) settingsRepository.customStreamUrl.first() else null,
        selectedStation = settingsRepository.selectedStation.first()
            ?.let { StreamSource(url = it.url, title = it.name, stationId = it.id) },
        qualityUrl = settingsRepository.streamQuality.first().url,
        defaultTitle = DEFAULT_STATION_NAME
    )

    /**
     * Points the player at [source], restarting playback only when the URL actually
     * changed so unrelated settings writes do not interrupt listening.
     */
    private fun applyStreamSource(source: StreamSource) {
        currentStationTitle = source.title
        if (source.url == currentStreamUrl) return
        currentStreamUrl = source.url
        replayBuffer.clear()
        playbackMode = PlaybackMode.Live
        val wasPlaying = player?.isPlaying == true
        player?.stop()
        player?.setMediaItem(buildMediaItem())
        player?.prepare()
        if (wasPlaying) player?.play()
        Log.d(TAG, "Stream source changed to ${source.title ?: source.url}")
    }

    private fun applyStreamQuality(quality: StreamQuality) {
        val newUrl = quality.url
        if (newUrl == currentStreamUrl) return
        currentStreamUrl = newUrl
        replayBuffer.clear()
        playbackMode = PlaybackMode.Live
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
        private const val DEFAULT_STREAM_URL = StreamConfig.DEFAULT_STREAM_URL
        private const val DEFAULT_STATION_NAME = "SIR"

        // Stream's static metadata values (not real track info)
        private const val STREAM_STATIC_TITLE = "Will Radio Stream"
        private const val STREAM_STATIC_ARTIST = "Live Internet Radio"

        // Media session & notification
        private const val MEDIA_SESSION_ID = "will_radio_session"
        private const val BROWSE_ROOT_ID = "sir_root"
        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 1001
        // Error retry

        // Feature flags
        const val SEEKBACK_ENABLED = false

        private const val SEEK_BACK_INCREMENT_MS = 30_000L

        // DVR time-shift buffer: 512KB ≈ 64s at 64kbps
        internal const val REPLAY_BUFFER_SIZE = 524_288
        private const val STREAM_BYTES_PER_SEC = 8_000L  // 64kbps

        // Intent actions
        private const val ACTION_STOP = "com.cascadiacollections.sir.action.STOP"
        const val ACTION_PLAY = "com.cascadiacollections.sir.action.PLAY"
        private const val ACTION_PAUSE = "com.cascadiacollections.sir.action.PAUSE"
        const val ACTION_SEEK_BACK = "com.cascadiacollections.sir.action.SEEK_BACK"
        const val ACTION_GO_LIVE = "com.cascadiacollections.sir.action.GO_LIVE"
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
