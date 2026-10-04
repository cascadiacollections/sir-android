package com.cascadiacollections.sir.wear

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.wear.sync.StationSyncListenerService
import com.cascadiacollections.sir.wear.sync.WatchStationStore
import kotlinx.coroutines.launch

class WearActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restored activity already acted on its launch intent.
        if (savedInstanceState == null) handlePlayLast(intent)
        // The listener only hears changes; catch up on what the phone already sent.
        lifecycleScope.launch { StationSyncListenerService.fetchLatest(this@WearActivity) }
        setContent {
            WearApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayLast(intent)
    }

    /** The "Play last" complication and the tile open this activity to start playback. */
    private fun handlePlayLast(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_PLAY_LAST, false) != true) return
        intent.removeExtra(EXTRA_PLAY_LAST)
        ContextCompat.startForegroundService(this, WearPlaybackService.playLastIntent(this))
    }

    private fun playStation(station: Station) {
        ContextCompat.startForegroundService(this, WearPlaybackService.playStationIntent(this, station))
    }

    private fun stop() {
        // Plain startService: stopping must not oblige the service to enter the foreground.
        startService(WearPlaybackService.stopIntent(this))
    }

    @Composable
    private fun WearApp() {
        var controller by remember { mutableStateOf<MediaController?>(null) }
        var isPlaying by remember { mutableStateOf(false) }
        var isBuffering by remember { mutableStateOf(false) }
        var stationName by remember { mutableStateOf<String?>(null) }
        var trackTitle by remember { mutableStateOf<String?>(null) }
        var currentStationId by remember { mutableStateOf<String?>(null) }

        val store = remember { WatchStationStore.from(this@WearActivity) }
        val payload by remember { store.payloads }.collectAsState(initial = store.load())
        val defaultName = stringResource(R.string.station_name)
        val stations = remember(payload, defaultName) {
            WearStations.list(WearStations.default(defaultName), payload)
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@WearActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        DisposableEffect(Unit) {
            val token = SessionToken(
                this@WearActivity,
                ComponentName(this@WearActivity, WearPlaybackService::class.java)
            )
            val future = MediaController.Builder(this@WearActivity, token).buildAsync()
            fun sync(player: Player) {
                isPlaying = player.isPlaying
                isBuffering = player.playbackState == Player.STATE_BUFFERING
                // The item's title is the station; the merged metadata title is the ICY
                // track once one arrives.
                stationName = player.currentMediaItem?.mediaMetadata?.title?.toString()
                trackTitle = player.mediaMetadata.title?.toString()?.takeIf { it != stationName }
                currentStationId = player.currentMediaItem?.mediaId
            }
            future.addListener({
                val ctrl = try {
                    future.get()
                } catch (_: Exception) {
                    return@addListener
                }
                controller = ctrl
                sync(ctrl)
                ctrl.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = sync(player)
                })
            }, ContextCompat.getMainExecutor(this@WearActivity))

            onDispose {
                controller?.release()
                controller = null
            }
        }

        MaterialTheme {
            AppScaffold(timeText = { TimeText() }) {
                val listState = rememberScalingLazyListState()
                ScreenScaffold(scrollState = listState) { contentPadding ->
                    WearHomeUi(
                        listState = listState,
                        contentPadding = contentPadding,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        stationName = stationName,
                        trackTitle = trackTitle,
                        stations = stations,
                        currentStationId = currentStationId,
                        onToggle = {
                            controller?.let { ctrl ->
                                if (ctrl.isPlaying) {
                                    ctrl.pause()
                                } else {
                                    ContextCompat.startForegroundService(
                                        this@WearActivity,
                                        Intent(this@WearActivity, WearPlaybackService::class.java)
                                    )
                                    ctrl.play()
                                }
                            } ?: ContextCompat.startForegroundService(
                                this@WearActivity,
                                Intent(this@WearActivity, WearPlaybackService::class.java)
                            )
                        },
                        onStop = ::stop,
                        onPlayStation = ::playStation
                    )
                }
            }
        }
    }

    companion object {
        /** Boolean extra: start playing the station last played on the phone. */
        const val EXTRA_PLAY_LAST = "com.cascadiacollections.sir.wear.extra.PLAY_LAST"
    }
}

/**
 * The watch home: Now Playing, Stop while something is playing, then Recent Stations —
 * the SIR stream followed by what the phone last played (ShoutKit's watch layout).
 */
@Composable
internal fun WearHomeUi(
    isPlaying: Boolean,
    isBuffering: Boolean,
    stationName: String?,
    trackTitle: String?,
    stations: List<Station>,
    currentStationId: String?,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onPlayStation: (Station) -> Unit,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
    contentPadding: PaddingValues = PaddingValues()
) {
    ScalingLazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "now_playing") {
            WearPlayerUi(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                trackTitle = trackTitle,
                onToggle = onToggle,
                stationName = stationName,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (isPlaying || isBuffering) {
            item(key = "stop") {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Filled.Stop, contentDescription = null) },
                    label = { Text(stringResource(R.string.stop)) }
                )
            }
        }
        item(key = "recent_header") {
            ListHeader { Text(stringResource(R.string.recent_stations)) }
        }
        items(stations, key = { "station_${it.id.ifBlank { it.streamUrl }}" }) { station ->
            val isCurrent = station.id.ifBlank { station.streamUrl } == currentStationId
            FilledTonalButton(
                onClick = { onPlayStation(station) },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                secondaryLabel = if (isCurrent) {
                    { Text(stringResource(R.string.now_playing)) }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
internal fun WearPlayerUi(
    isPlaying: Boolean,
    isBuffering: Boolean,
    trackTitle: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    stationName: String? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stationName ?: stringResource(R.string.station_name),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            trackTitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            if (isBuffering) {
                CircularProgressIndicator(modifier = Modifier.size(IconButtonDefaults.LargeButtonSize))
            } else {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(IconButtonDefaults.LargeButtonSize)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play)
                    )
                }
            }
            if (isPlaying) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.live),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
