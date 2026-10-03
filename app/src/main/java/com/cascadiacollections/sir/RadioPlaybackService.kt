package com.cascadiacollections.sir

import android.Manifest
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.DefaultMediaNotificationProvider.NotificationIdProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.cascadiacollections.android.media3.timeshift.PlaybackMode
import com.cascadiacollections.android.media3.timeshift.TimeShiftController
import com.cascadiacollections.android.media3.timeshift.TimeShiftDataSource
import com.cascadiacollections.sir.core.directory.search
import com.cascadiacollections.sir.core.persistence.HeardTrack
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.core.persistence.StationCollections
import com.cascadiacollections.sir.core.persistence.TrackHistoryRepository
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.AudioRoutePolicy
import com.cascadiacollections.sir.core.playback.EqualizerCurves
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.PlaybackBufferConfig
import com.cascadiacollections.sir.core.playback.PlaybackLocks
import com.cascadiacollections.sir.core.playback.RawStreamMetadata
import com.cascadiacollections.sir.core.playback.RetryBackoff
import com.cascadiacollections.sir.core.playback.SleepTimerRestore
import com.cascadiacollections.sir.core.playback.StallCeiling
import com.cascadiacollections.sir.core.playback.StreamConfig
import com.cascadiacollections.sir.core.playback.StreamEndpoint
import com.cascadiacollections.sir.core.playback.StreamEndpoints
import com.cascadiacollections.sir.core.playback.StreamUrlKind
import com.cascadiacollections.sir.core.playback.StreamFailure
import com.cascadiacollections.sir.core.playback.StreamMetadata
import com.cascadiacollections.sir.core.playback.StreamMetadataResolver
import com.cascadiacollections.sir.core.playback.StreamSource
import com.cascadiacollections.sir.core.playback.StreamSourceResolver
import com.cascadiacollections.sir.notificationcolors.NotificationAccentColor
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

class RadioPlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val audioManager: AudioManager by lazy { getSystemService(AudioManager::class.java) }
    private var isNoisyReceiverRegistered = false
    private var isRouteReceiverRegistered = false
    private val audioRoutePolicy = AudioRoutePolicy()
    // Each prepare makes three load attempts, so six total load attempts permit one re-prepare.
    private val retryBackoff = RetryBackoff(
        maxRetries = StreamLoadErrorHandlingPolicy.MAX_PREPARE_ATTEMPTS - 1
    )
    private val stallCeiling = StallCeiling()

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
    // A custom curve and a named preset are mutually exclusive; this flag says which of
    // currentEqualizerPreset / currentCustomEqualizerBands is currently in effect.
    private var isUsingCustomEqualizerBands: Boolean = false
    private var currentCustomEqualizerBands: List<Float> = emptyList()
    // Generated ourselves in onCreate so the equalizer can be constructed without racing
    // renderer initialization. Media3's C.AUDIO_SESSION_ID_UNSET is @UnstableApi and is
    // defined as this exact constant, so using the platform one keeps the property
    // declaration free of an opt-in requirement.
    private var audioSessionId: Int = AudioManager.AUDIO_SESSION_ID_GENERATE

    // Settings and coroutine scope
    private val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current stream URL (may be a directory station or a debug override). This is the
    // station's own URL, used to tell whether a selection actually changed; what the
    // player opens is currentEndpoint, which differs when the URL is a .pls/.m3u playlist.
    private var currentStreamUrl: String = DEFAULT_STREAM_URL

    // What the player is actually pointed at for currentStreamUrl. Kept for the life of
    // the selection, so reconnects re-prepare the same item and never re-fetch the playlist.
    private var currentEndpoint: StreamEndpoint = StreamEndpoint(DEFAULT_STREAM_URL)

    // The most recent source anything asked to adopt. A playlist fetch suspends, so an
    // older request can finish after a newer one; it must not win.
    private var requestedStreamUrl: String = DEFAULT_STREAM_URL

    // In-flight or finished playlist resolution, keyed by the URL it is for, so the
    // selection collector and onAddMediaItems resolving the same station share one fetch.
    private var endpointResolution: Pair<String, Deferred<StreamEndpoint>>? = null

    private val playlistFetcher by lazy {
        StationPlaylistFetcher(StreamingHttpClientProvider.client, USER_AGENT)
    }

    // Display title for the current stream; null falls back to the bundled station name
    private var currentStationTitle: String? = null

    // Directory id of the current stream (null for the app's own stream), for track history
    private var currentStationId: String? = null

    // Cover art for the current track (iTunes Search), gated on the privacy setting
    private val albumArt by lazy {
        AlbumArtResolver(serviceScope, settingsRepository.fetchAlbumArtwork, AppAlbumArt::lookup) {
            publishResolvedMetadata()
        }
    }

    // The typed failure last published to controllers as session extras (see reportFailure)
    private var reportedFailure: StreamFailure? = null
    private var reportedFailureRetrying = false

    // Persisted Recently Heard history, recorded here so it accrues without any UI alive
    private val trackHistoryRepository: TrackHistoryRepository by lazy { TrackHistoryRepository(this) }

    // DVR time-shift buffer
    private val timeShift = TimeShiftController(REPLAY_BUFFER_SIZE, STREAM_BYTES_PER_SEC)
    private var playbackMode: PlaybackMode = PlaybackMode.Live

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

        // Media3's own provider owns the channel, foreground promotion/demotion, and
        // POST_NOTIFICATIONS handling (media-session notifications are exempt by policy).
        // The seek-back-action override this used to need is gone: SEEKBACK_ENABLED is
        // false, and once it lands, updateCustomLayout()'s CommandButtons are picked up
        // automatically without any provider customization. createNotification() is
        // final, so the brand accent color — shared with Wear via NotificationAccentColor
        // — is applied in the addNotificationActions() hook instead, the one point the
        // provider hands back its NotificationCompat.Builder.
        setMediaNotificationProvider(
            object : DefaultMediaNotificationProvider(
                context,
                NotificationIdProvider { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.notification_channel_name
            ) {
                override fun addNotificationActions(
                    mediaSession: MediaSession,
                    mediaButtons: ImmutableList<CommandButton>,
                    builder: NotificationCompat.Builder,
                    actionFactory: MediaNotification.ActionFactory
                ): IntArray {
                    NotificationAccentColor.applyTo(builder)
                    return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
                }
            }
        )

        // Load settings asynchronously
        serviceScope.launch {
            applyStreamSource(resolveStreamSource())

            // Load and apply the equalizer, whichever mode it was last left in
            isUsingCustomEqualizerBands = settingsRepository.equalizerUseCustomBands.first()
            if (isUsingCustomEqualizerBands) {
                currentCustomEqualizerBands = settingsRepository.equalizerCustomBands.first()
            } else {
                currentEqualizerPreset = settingsRepository.equalizerPreset.first()
            }

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

        albumArt.start()

        // React to the user picking a different station while the service is alive
        serviceScope.launch {
            settingsRepository.selectedStation
                .drop(1)
                .collect {
                    // A selection change is always a deliberate user action — tapping a
                    // station, or "back to SIR" — so it starts playback rather than
                    // silently re-pointing a stopped player.
                    applyStreamSource(resolveStreamSource(), startPlayback = true)
                }
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
        val okHttpClient = StreamingHttpClientProvider.client

        // OkHttp data source for better HTTP performance (HTTP/2, connection reuse)
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(
                buildMap {
                    put("Icy-MetaData", "1")  // Request ICY metadata
                    put("User-Agent", USER_AGENT)
                }
            )

        // Bandwidth meter for adaptive streaming (though this stream is fixed bitrate)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setResetOnNetworkTypeChange(true)
            .build()

        // Time-shift data source wraps OkHttp for DVR-style replay
        val timeShiftFactory = TimeShiftDataSource.Factory(httpDataSourceFactory, timeShift)

        // Progressive streams read through the time-shift buffer; HLS goes straight to
        // OkHttp, because the buffer models one continuous connection and HLS is many.
        val mediaSourceFactory = StreamMediaSourceFactory(
            progressive = DefaultMediaSourceFactory(context).setDataSourceFactory(timeShiftFactory),
            hls = HlsMediaSource.Factory(httpDataSourceFactory),
        ).setLoadErrorHandlingPolicy(StreamLoadErrorHandlingPolicy())

        // Generate the audio session id ourselves so it's available immediately,
        // avoiding a race with renderer initialization (player.audioSessionId can
        // be C.AUDIO_SESSION_ID_UNSET right after prepare()).
        //
        // generateAudioSessionId() reports failure as AudioManager.ERROR, which is not a
        // usable session id. Fall back to UNSET so the player generates its own; the
        // equalizer then skips attaching rather than binding to an invalid session.
        val generatedAudioSessionId = audioManager.generateAudioSessionId()
            .takeIf { it != AudioManager.ERROR }
            ?: C.AUDIO_SESSION_ID_UNSET
        if (generatedAudioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            Log.w(TAG, "generateAudioSessionId() failed; equalizer will be unavailable")
        }
        audioSessionId = generatedAudioSessionId

        // Create optimized ExoPlayer
        val exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(SEEK_BACK_INCREMENT.inWholeMilliseconds)
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

        // Builder has no audio-session setter, so assign the pre-generated id on the player,
        // before the first prepare() so the renderer adopts it. Kept out of the apply block
        // above because lint does not carry this method's opt-in into that lambda.
        exoPlayer.setAudioSessionId(generatedAudioSessionId)

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
                            if (!timeShift.seekBack(SEEK_BACK_INCREMENT)) {
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                            }
                            playbackMode = PlaybackMode.TimeShifted
                            flushPlayer()
                            updateCustomLayout()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        ACTION_GO_LIVE -> {
                            if (!SEEKBACK_ENABLED) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                            timeShift.goLive()
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

                // The car browser lists the SIR stream first, then the user's saved
                // stations, so the library is reachable without touching the phone.
                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: MediaLibraryService.LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    if (parentId != BROWSE_ROOT_ID) {
                        return Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.of(), params)
                        )
                    }
                    return serviceScope.future {
                        val stations = settingsRepository.savedStations.first()
                        val items = ImmutableList.builder<MediaItem>()
                            .add(buildMediaItem())
                            .addAll(stations.filter { it.isPlayable }.map(::buildStationMediaItem))
                            .build()
                        LibraryResult.ofItemList(items, params)
                    }
                }

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String
                ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
                    val station = settingsRepository.savedStations.first()
                        .firstOrNull { it.id == mediaId }
                    if (station == null) {
                        LibraryResult.ofItem(buildMediaItem(), null)
                    } else {
                        LibraryResult.ofItem(buildStationMediaItem(station), null)
                    }
                }

                // Selecting a station in the car goes through the same persisted
                // selection as the phone UI, so both stay in sync and the choice
                // survives the service being restarted.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
                    val requestedId = mediaItems.firstOrNull()?.mediaId
                    val station = requestedId
                        ?.let { id -> settingsRepository.savedStations.first().firstOrNull { it.id == id } }
                    if (station != null) {
                        settingsRepository.selectStation(station)
                    } else if (requestedId != null) {
                        settingsRepository.clearSelectedStation()
                    }
                    // Resolve rather than using the station directly, so the car honours
                    // the same precedence as the phone, and adopt the result here so the
                    // collector watching the selection no-ops instead of racing us.
                    mutableListOf(adoptStreamSource(resolveStreamSource()) ?: buildMediaItem())
                }

                // Voice search ("Play [station name]") arrives here rather than through
                // onAddMediaItems: Media3's legacy bridge translates
                // MediaSessionCompat.Callback#onPlayFromSearch into a single MediaItem
                // whose RequestMetadata carries the raw query, delivered through
                // onSetMediaItems rather than onAddMediaItems.
                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val query = mediaItems.firstOrNull()?.requestMetadata?.searchQuery
                    if (query.isNullOrBlank()) {
                        return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
                    }
                    return serviceScope.future {
                        resolveVoiceSearch(query)?.let { settingsRepository.selectStation(it) }
                        val item = adoptStreamSource(resolveStreamSource()) ?: buildMediaItem()
                        MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET)
                    }
                }
            })
            .setId(MEDIA_SESSION_ID)
            .setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
            // No initial custom layout — updateCustomLayout() adds "Replay 30s"
            // once the buffer has enough data. Calling setCustomLayout with an
            // empty list crashes the legacy PlaybackStateCompat stub.

        // Now add listeners after mediaSession is created
        // Media3 already measures what a tap-to-audio trace would: join time and
        // rebuffering, reported when a playback session ends (a station switch, or the
        // player being released). Logged rather than wired to analytics so the `foss`
        // flavor gets the same numbers; `:benchmark` and logcat can both read them.
        exoPlayer.addAnalyticsListener(
            PlaybackStatsListener(/* keepHistory = */ false) { _, stats ->
                Log.i(
                    TAG_PLAYBACK_STATS,
                    "joinMs=${stats.getTotalJoinTimeMs()} " +
                        "playbackMs=${stats.getTotalPlayTimeMs()} " +
                        "rebuffers=${stats.totalRebufferCount} " +
                        "rebufferMs=${stats.getTotalRebufferTimeMs()} " +
                        "maxRebufferMs=${stats.maxRebufferTimeMs} " +
                        "fatalErrors=${stats.fatalErrorCount}"
                )
            }
        )

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        retryBackoff.reset()
                        stallCeiling.clear()
                        reportFailure(null)
                        if (player?.playWhenReady == true) {
                            updateCustomLayout()
                        }
                    }

                    Player.STATE_ENDED -> {
                        stallCeiling.clear()
                        handleUnexpectedEnd()
                    }

                    // Arm the stall ceiling only when we actually want audio: a manual
                    // pause leaves the player briefly buffering on its way to a stop, and
                    // that is not a stall.
                    // A buffer with no reconnect of ours pending is the user retrying a
                    // failure we had given up on, so the failure is over.
                    Player.STATE_BUFFERING -> {
                        armStallCeiling()
                        if (!reportedFailureRetrying) reportFailure(null)
                    }

                    // Idle only follows a stop we asked for or a re-prepare on its way to
                    // buffering. Clear here too: a user-initiated stop while a stall was
                    // armed must not let the delayed callback fire later and spend a
                    // reconnect attempt on playback nobody wants anymore.
                    Player.STATE_IDLE -> stallCeiling.clear()
                }
            }

            // Pausing (or cancelling a connection attempt) dismisses a failure: the listener
            // has acknowledged it, and the transport should offer Play again, not Retry.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) reportFailure(null)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    // Every transport route ends here — notification, in-app, Auto, Wear,
                    // Bluetooth — so this is the one place that sees all resumes.
                    audioRoutePolicy.onPlaybackStarted()
                    playbackLocks?.acquire()
                    if (SEEKBACK_ENABLED) scheduleSeekBackReveal()
                } else {
                    playbackLocks?.release()
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

                val stationName = currentStationTitle ?: getString(R.string.station_name)
                val previous = streamMetadata
                val update = metadataResolver.resolve(
                    previous = previous,
                    raw = raw,
                    stationName = stationName,
                )
                streamMetadata = update.metadata
                val artCleared = albumArt.onTrackChanged(currentArtist, currentTrackTitle)
                if (update.notifyChanged || artCleared) publishResolvedMetadata()
                recordHeardTrack(previous, update.metadata, stationName)
            }

            override fun onPlayerError(error: PlaybackException) {
                stallCeiling.clear()
                val failure = error.toStreamFailure()
                Log.e(TAG, "Player error (attempt ${retryBackoff.attemptLabel}, $failure)", error)
                if (!failure.isRetryable) {
                    // A station that answers 404, or sends a codec this device can't
                    // decode, fails identically on every attempt. Spending the backoff on
                    // it held a wake lock for about a minute to show the same message.
                    retryBackoff.reset()
                    reportFailure(failure)
                    return
                }
                val delayMs = retryBackoff.nextDelayMs()
                reportFailure(failure, retrying = delayMs != null)
                if (delayMs != null) {
                    sleepTimerHandler.postDelayed({ reconnectIfWanted() }, delayMs)
                } else {
                    Log.e(TAG, "Retries exhausted: $failure")
                }
            }
        })

        // Now prepare the player
        player?.prepare()

        // Initialize equalizer with the player's audio session
        initializeEqualizer()

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
                // A stall ceiling give-up leaves the player idle rather than failed, so
                // resuming it here has to re-prepare rather than just resume.
                if (player?.playbackState == Player.STATE_IDLE) {
                    player?.prepare()
                }
                player?.play()
            }

            ACTION_PAUSE -> {
                player?.pause()
            }

            // Cold-start voice search: SIR wasn't already the active media app, so this
            // arrived via the MEDIA_PLAY_FROM_SEARCH manifest intent-filter rather than
            // through the session (see onSetMediaItems for the already-playing case).
            ACTION_PLAY_FROM_SEARCH -> {
                val query = intent.getStringExtra(EXTRA_SEARCH_QUERY)
                serviceScope.launch {
                    val station = query?.takeIf { it.isNotBlank() }?.let { resolveVoiceSearch(it) }
                    if (station != null) {
                        // Picked up by the selectedStation collector in onCreate, which
                        // starts playback itself — nothing further to do here.
                        settingsRepository.selectStation(station)
                    } else {
                        if (player?.playbackState == Player.STATE_IDLE) player?.prepare()
                        player?.play()
                    }
                }
            }

            ACTION_SEEK_BACK -> {
                if (SEEKBACK_ENABLED) {
                    if (timeShift.seekBack(SEEK_BACK_INCREMENT)) {
                        playbackMode = PlaybackMode.TimeShifted
                        flushPlayer()
                        updateCustomLayout()
                    }
                }
            }

            ACTION_GO_LIVE -> {
                if (SEEKBACK_ENABLED) {
                    timeShift.goLive()
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

            ACTION_SET_EQUALIZER_BANDS -> {
                // Fired on every slider-drag tick, so this only ever applies live audio
                // feedback — persist = false. The UI persists once itself, directly via
                // SettingsRepository, when the drag gesture ends (see SettingsScreen's
                // onGainsSettled), rather than writing to DataStore on every tick here.
                val bands = intent.getFloatArrayExtra(EXTRA_EQUALIZER_BANDS)?.toList()
                if (bands != null) applyCustomEqualizerBands(bands, persist = false)
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
        timeShift.reset()

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

    /**
     * Appends a newly resolved track to the persisted Recently Heard history.
     *
     * Only a track the resolver actually moved to counts: a rejected or placeholder update
     * keeps the previous track in [next], and recording that would file the last station's
     * song under a station that has since been switched to. Consecutive repeats that do
     * get through (a service restart re-announcing the current track) are merged by
     * `HeardTracks.record`.
     */
    private fun recordHeardTrack(previous: StreamMetadata, next: StreamMetadata, stationName: String) {
        val title = next.trackTitle ?: return
        if (title == previous.trackTitle && next.artist == previous.artist) return
        val track = HeardTrack(
            title = title,
            artist = next.artist,
            stationId = currentStationId,
            stationName = stationName,
            timestampMillis = System.currentTimeMillis(),
        )
        serviceScope.launch { trackHistoryRepository.record(track) }
    }

    /**
     * Pushes the resolver's deduped title/artist onto the current [MediaItem] so the
     * session-owned notification (and lock screen, Auto, Wear) show them instead of the
     * raw ICY tags, which repeat the stream's static placeholder text between tracks.
     */
    private fun publishResolvedMetadata() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val art = albumArt.current?.takeIf { currentTrackTitle != null }
        val resolved = item.mediaMetadata.buildUpon()
            .setTitle(currentTrackTitle ?: currentStation ?: getString(R.string.station_name))
            .setArtist(currentArtist ?: getString(R.string.stream_description))
            // A locale-independent, non-user-facing signal that this update carries a
            // real resolved ICY track rather than the station-name/generic-description
            // fallback — RadioViewModel uses it to decide what belongs in track
            // history. Comparing the *displayed* title/artist text against a
            // translated fallback string would break the moment either happened to
            // equal it, or the two ran under different configurations.
            //
            // Copy the existing extras rather than replacing them outright — this item
            // already carries EXTRA_STREAM_URL from buildMediaItem(), which the :cast
            // module reads for Chromecast handoff, and a bare Bundle() here would wipe it.
            //
            // Album art (when the lookup found some) is both the session artwork — so the
            // notification, lock screen and Auto show it — and an extra the phone UI reads.
            .setArtworkUri(art?.artworkUrl?.let(Uri::parse))
            .setExtras(
                (item.mediaMetadata.extras?.let { Bundle(it) } ?: Bundle()).apply {
                    putBoolean(EXTRA_HAS_RESOLVED_TRACK, currentTrackTitle != null)
                    putString(EXTRA_ALBUM_ART_URL, art?.artworkUrl)
                    putString(EXTRA_TRACK_VIEW_URL, art?.trackViewUrl)
                }
            )
            .build()
        p.replaceMediaItem(p.currentMediaItemIndex, item.buildUpon().setMediaMetadata(resolved).build())
    }

    /**
     * A live stream has no end, so `STATE_ENDED` means the server closed the connection.
     *
     * That arrives *without* an `onPlayerError`, so nothing else here would notice: the
     * player sat ended, the notification kept claiming the station was on, and the reconnect
     * backoff — which only ran from the error path — never saw it. Rejoin through the same
     * bounded schedule a player error uses. (ShoutKit reached this from the other side: its
     * engine reports an unrequested stop as a retryable failure. See
     * `docs/audioplayer-dependency-synergies.md`.)
     */
    private fun handleUnexpectedEnd() {
        // Only when audio was wanted. A deliberate stop leaves the player idle rather than
        // ended, but a paused player must not be restarted by this either.
        if (player?.playWhenReady != true) return

        Log.w(TAG, "Live stream ended unexpectedly (attempt ${retryBackoff.attemptLabel})")
        val delayMs = retryBackoff.nextDelayMs()
        reportFailure(StreamFailure.Transient, retrying = delayMs != null)
        if (delayMs != null) {
            sleepTimerHandler.postDelayed({ reconnectIfWanted() }, delayMs)
        } else {
            Log.e(TAG, "Retries exhausted after unexpected end")
        }
    }

    /**
     * Fires [StallCeiling.timeoutDelayMs] after [StallCeiling.arm]. If [token] is stale —
     * the stall cleared, or a new one was armed, before this ran — it is a no-op. Also
     * bails if audio is no longer wanted: a pause while still `STATE_BUFFERING` doesn't
     * change [Player.getPlaybackState] and so wouldn't otherwise clear the ceiling, and a
     * released player must never be spent a reconnect attempt on.
     *
     * Spends one reconnect attempt through the existing [retryBackoff] budget; once that
     * is exhausted, stops the player rather than keep looping on a connection that never
     * recovers. Mirrors ShoutKit: the terminal state is a stop, not a reported failure, so
     * the transport shows a play button rather than an error icon — [ACTION_PLAY] restarts
     * the stream when it finds the player idle.
     */
    private fun onStallCeilingExpired(token: Int) {
        if (!stallCeiling.isCurrent(token)) return
        if (player?.playWhenReady != true) {
            stallCeiling.clear()
            return
        }
        Log.w(TAG, "Stream stalled past the ceiling (attempt ${retryBackoff.attemptLabel})")
        val delayMs = retryBackoff.nextDelayMs()
        reportFailure(StreamFailure.Stalled, retrying = delayMs != null)
        if (delayMs != null) {
            // Re-arm explicitly rather than relying on a fresh STATE_BUFFERING callback:
            // a player already sitting in STATE_BUFFERING may not emit one for prepare(),
            // which would otherwise leave this reconnect attempt with no ceiling of its own.
            sleepTimerHandler.postDelayed(
                {
                    reconnectIfWanted()
                    armStallCeiling()
                },
                delayMs
            )
            return
        }
        retryBackoff.reset()
        player?.stop()
        reportFailure(StreamFailure.Stalled)
        Log.e(TAG, "Gave up: ${StreamFailure.Stalled}")
    }

    /**
     * A scheduled reconnect, skipped when the listener paused or cancelled in the meantime —
     * otherwise "Cancel connection" would be undone by the backoff a few seconds later.
     */
    private fun reconnectIfWanted() {
        if (player?.playWhenReady == true) player?.prepare()
    }

    /**
     * Publishes the typed failure (or its absence) to every controller as session extras,
     * where `RadioViewModel` turns it into ShoutKit's error copy. [retrying] says a reconnect
     * is scheduled or in flight, which the UI shows as "Reconnecting…".
     */
    private fun reportFailure(failure: StreamFailure?, retrying: Boolean = false) {
        val isRetrying = failure != null && retrying
        if (failure == reportedFailure && isRetrying == reportedFailureRetrying) return
        reportedFailure = failure
        reportedFailureRetrying = isRetrying
        mediaSession?.setSessionExtras(PlaybackFailureExtras.bundle(failure, isRetrying))
    }

    /** Arms [stallCeiling] and schedules its expiry check, if audio is still wanted. */
    private fun armStallCeiling() {
        if (player?.playWhenReady != true) return
        val token = stallCeiling.arm()
        sleepTimerHandler.postDelayed({ onStallCeilingExpired(token) }, stallCeiling.timeoutDelayMs)
    }

    /**
     * Force ExoPlayer to discard its internal decoded buffer and re-read from
     * the [timeShift] buffer at the current read cursor position.
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
        if (timeShift.canSeekBack(SEEK_BACK_INCREMENT)) {
            updateCustomLayout()
            return
        }
        val runnable = Runnable { updateCustomLayout() }
        seekBackRevealRunnable = runnable
        // Check shortly after 30s of buffering; add 2s margin for network jitter
        sleepTimerHandler.postDelayed(runnable, SEEK_BACK_INCREMENT.inWholeMilliseconds + 2_000)
    }

    private fun updateCustomLayout() {
        val session = mediaSession ?: return
        val canSeek = timeShift.canSeekBack(SEEK_BACK_INCREMENT)

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
        // legacy PlaybackStateCompat CustomAction builder (requires icon). The session's
        // own notification manager picks up the change and refreshes the notification.
        if (buttons.isNotEmpty()) {
            session.setCustomLayout(ImmutableList.copyOf(buttons))
        }
    }

    private fun buildMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(currentEndpoint.url)
        .setMimeType(if (currentEndpoint.isHls) MimeTypes.APPLICATION_M3U8 else null)
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
                // MediaItem.LocalConfiguration (the setUri above) never crosses the
                // MediaController boundary, by Media3 design — only mediaId and
                // mediaMetadata do. The :cast module needs the actual playable URL to
                // start a Chromecast session from a MediaController of its own (it
                // can't reach this service's private fields), so it rides along here.
                // It is the resolved endpoint, not a .pls/.m3u wrapper, and carries the HLS
                // MIME type when there is one, since a receiver can't follow either itself.
                .setExtras(
                    Bundle().apply {
                        putString(EXTRA_STREAM_URL, currentEndpoint.url)
                        if (currentEndpoint.isHls) putString(EXTRA_STREAM_MIME_TYPE, MimeTypes.APPLICATION_M3U8)
                    }
                )
                .build()
        )
        .build()

    // A browse item only has to identify the station: picking it goes through
    // onAddMediaItems, which resolves the persisted selection (and any playlist) itself.
    private fun buildStationMediaItem(station: Station): MediaItem = MediaItem.Builder()
        .setUri(station.streamUrl)
        .setMimeType(
            if (StreamEndpoints.classify(station.streamUrl, station.isHls) == StreamUrlKind.HLS) {
                MimeTypes.APPLICATION_M3U8
            } else {
                null
            }
        )
        .setMediaId(station.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist(station.displayLabel)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                // Auto/Automotive load this URI themselves (their own image pipeline), so
                // it's safe to pass through even though the phone UI has no image loader.
                .setArtworkUri(station.favicon?.takeIf { it.isNotBlank() }?.let(Uri::parse))
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
            val sessionId = audioSessionId
            if (sessionId == C.AUDIO_SESSION_ID_UNSET) {
                Log.w(TAG, "Audio session ID not set, skipping equalizer init")
                return
            }

            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
            }
            if (isUsingCustomEqualizerBands) {
                applyCustomEqualizerBands(currentCustomEqualizerBands, persist = false)
            } else {
                applyEqualizerPreset(currentEqualizerPreset, persist = false)
            }
            Log.d(TAG, "Equalizer initialized with session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize equalizer", e)
        }
    }

    private fun applyEqualizerPreset(preset: EqualizerPreset, persist: Boolean = true) {
        currentEqualizerPreset = preset
        isUsingCustomEqualizerBands = false
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

            if (persist) {
                serviceScope.launch { settingsRepository.setEqualizerPreset(preset) }
            }

            Log.d(TAG, "Applied equalizer preset: ${preset.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply equalizer preset", e)
        }
    }

    /**
     * Resolves a voice-search query ("Play [station name]") to a station.
     *
     * Saved stations are checked first — an exact match is what the user almost
     * certainly meant, and it works with no network round trip — before falling back
     * to the top live directory result for stations the user hasn't saved.
     */
    private suspend fun resolveVoiceSearch(query: String): Station? {
        val saved = settingsRepository.savedStations.first()
        StationCollections.findByName(saved, query)?.let { return it }
        return AppDirectory.instance.search(query, limit = 1).getOrNull()?.firstOrNull()
    }

    /**
     * Applies a custom equalizer curve — one gain per UI slider, interpolated across
     * however many hardware bands the device actually has.
     */
    private fun applyCustomEqualizerBands(bands: List<Float>, persist: Boolean = true) {
        currentCustomEqualizerBands = bands
        isUsingCustomEqualizerBands = true
        val eq = equalizer ?: return

        try {
            val levels = EqualizerCurves.levelsForCustomBands(
                gains = bands,
                bandCount = eq.numberOfBands.toInt(),
                minLevel = eq.bandLevelRange[0],
                maxLevel = eq.bandLevelRange[1]
            )

            levels.forEachIndexed { band, level ->
                eq.setBandLevel(band.toShort(), level)
            }

            if (persist) {
                serviceScope.launch { settingsRepository.setEqualizerCustomBands(bands) }
            }

            Log.d(TAG, "Applied custom equalizer bands: $bands")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply custom equalizer bands", e)
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
            ?.let { StreamSource(url = it.streamUrl, title = it.name, stationId = it.id, isHls = it.isHls) },
        qualityUrl = settingsRepository.streamQuality.first().url,
        defaultTitle = DEFAULT_STATION_NAME
    )

    /**
     * Adopts [source] as the current stream without touching the player, returning the
     * item the player should be given — or null when [source] is already current.
     *
     * Two paths point the player at a new stream: this service reacting to the persisted
     * selection, and Media3 setting whatever `onAddMediaItems` returns. Both now go
     * through here, so the bookkeeping happens exactly once and whichever runs second
     * sees an unchanged URL and no-ops. Previously they each built their own item — one
     * with the live configuration and one without — and whichever landed last won.
     *
     * A `.pls`/`.m3u` URL is resolved to the stream it names first, which suspends for a
     * fetch; direct and HLS URLs don't. If a newer source was requested meanwhile, this
     * one is dropped rather than overwriting it.
     */
    private suspend fun adoptStreamSource(source: StreamSource): MediaItem? {
        currentStationTitle = source.title
        currentStationId = source.stationId
        requestedStreamUrl = source.url
        if (source.url == currentStreamUrl) return null
        val endpoint = endpointFor(source)
        if (source.url == currentStreamUrl || source.url != requestedStreamUrl) return null
        currentStreamUrl = source.url
        currentEndpoint = endpoint
        // Only the current station's resolution is worth keeping: picking another
        // station and coming back is a new selection and fetches the playlist again.
        if (endpointResolution?.first != source.url) endpointResolution = null
        timeShift.reset()
        playbackMode = PlaybackMode.Live
        return buildMediaItem()
    }

    /**
     * The endpoint to open for [source], fetching its playlist when it is one. Concurrent
     * callers for the same URL share one fetch; failures fall back to the URL itself.
     */
    private suspend fun endpointFor(source: StreamSource): StreamEndpoint {
        StreamEndpoints.withoutFetch(source.url, source.isHls)?.let { return it }
        val pending = endpointResolution?.takeIf { it.first == source.url }?.second
            ?: serviceScope.async { playlistFetcher.resolve(source.url) }
                .also { endpointResolution = source.url to it }
        return pending.await()
    }

    /**
     * Points the player at [source], restarting playback only when the URL actually
     * changed so unrelated settings writes do not interrupt listening.
     *
     * [startPlayback] forces playback to begin even from a stopped player. Picking a
     * station is a request to hear it, but on a cold launch the player is prepared with
     * `playWhenReady = false`, so inferring intent from "were we already playing" meant
     * a tap selected the station and then sat silent.
     */
    private suspend fun applyStreamSource(source: StreamSource, startPlayback: Boolean = false) {
        val item = adoptStreamSource(source) ?: return
        // A new station starts with a clean slate; its own failures are reported afresh.
        reportFailure(null)
        // Read after adopting: a playlist fetch may have suspended, and the old stream
        // keeps playing meanwhile.
        val wasPlaying = player?.isPlaying == true
        player?.stop()
        player?.setMediaItem(item)
        player?.prepare()
        if (wasPlaying || startPlayback) player?.play()
        Log.d(TAG, "Stream source changed to ${source.title ?: source.url}")
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

        /** Distinct tag so join/rebuffer numbers can be scraped without the rest of the log. */
        private const val TAG_PLAYBACK_STATS = "SirPlaybackStats"

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

        private val SEEK_BACK_INCREMENT = 30.seconds

        // DVR time-shift buffer: 512KB ≈ 64s at 64kbps
        internal const val REPLAY_BUFFER_SIZE = 524_288
        private const val STREAM_BYTES_PER_SEC = 8_000  // 64kbps

        // Intent actions
        private const val ACTION_STOP = "com.cascadiacollections.sir.action.STOP"
        const val ACTION_PLAY = "com.cascadiacollections.sir.action.PLAY"
        private const val ACTION_PAUSE = "com.cascadiacollections.sir.action.PAUSE"
        const val ACTION_SEEK_BACK = "com.cascadiacollections.sir.action.SEEK_BACK"
        const val ACTION_GO_LIVE = "com.cascadiacollections.sir.action.GO_LIVE"
        const val ACTION_SET_SLEEP_TIMER = "com.cascadiacollections.sir.action.SET_SLEEP_TIMER"
        const val ACTION_SET_EQUALIZER = "com.cascadiacollections.sir.action.SET_EQUALIZER"
        const val ACTION_PLAY_FROM_SEARCH = "com.cascadiacollections.sir.action.PLAY_FROM_SEARCH"
        const val ACTION_SET_EQUALIZER_BANDS = "com.cascadiacollections.sir.action.SET_EQUALIZER_BANDS"

        // Intent extras
        const val EXTRA_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
        const val EXTRA_EQUALIZER_PRESET = "equalizer_preset"
        const val EXTRA_SEARCH_QUERY = "search_query"
        const val EXTRA_EQUALIZER_BANDS = "equalizer_bands"

        /**
         * [MediaMetadata.extras] key: whether the current metadata update carries a
         * genuinely resolved ICY track, as opposed to the station-name/generic
         * fallback. See [publishResolvedMetadata].
         */
        const val EXTRA_HAS_RESOLVED_TRACK = "has_resolved_track"

        /**
         * [MediaMetadata.extras] key for the actual playable stream URL of the current
         * item. Public (unlike the fields it mirrors) because MediaItem's own URI
         * (`LocalConfiguration`) never crosses the MediaController boundary — this is
         * how a controller elsewhere in the process, such as the :cast module's own
         * MediaController, can learn the real URL to hand to a Chromecast session.
         */
        const val EXTRA_STREAM_URL = "stream_url"

        /**
         * [MediaMetadata.extras] key for the MIME type of [EXTRA_STREAM_URL], present only
         * when the player was told one explicitly (HLS). Absent means a progressive stream.
         */
        const val EXTRA_STREAM_MIME_TYPE = "stream_mime_type"

        /** [MediaMetadata.extras] key: the current track's album art URL, when one was found. */
        const val EXTRA_ALBUM_ART_URL = "album_art_url"

        /** [MediaMetadata.extras] key: the current track's Apple Music page, when one was found. */
        const val EXTRA_TRACK_VIEW_URL = "track_view_url"

        private val USER_AGENT = "SIR Android/${Build.VERSION.SDK_INT}"
    }
}
