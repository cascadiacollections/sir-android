@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cascadiacollections.sir

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cascadiacollections.sir.ui.RadioUi
import com.cascadiacollections.sir.ui.SettingsSheet
import com.cascadiacollections.sir.ui.theme.SirTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val RADIO_TAG = "RadioScreen"
private const val ACTION_SHORTCUT_PLAY = "com.cascadiacollections.sir.SHORTCUT_PLAY"

class MainActivity : ComponentActivity() {

    private lateinit var castDeviceDetector: CastDeviceDetector
    private lateinit var castFeatureManager: CastFeatureManager
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize Cast detection and feature management
        castDeviceDetector = CastDeviceDetector(this)
        castFeatureManager = CastFeatureManager(this)
        settingsRepository = SettingsRepository(this)

        // Only detect Cast devices if module not already installed
        if (!castFeatureManager.isModuleInstalled()) {
            lifecycle.addObserver(castDeviceDetector)
        }

        // Handle home-screen shortcut: start playback immediately
        if (intent?.action == ACTION_SHORTCUT_PLAY) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RadioPlaybackService::class.java).apply {
                    action = RadioPlaybackService.ACTION_PLAY
                }
            )
        }

        enableEdgeToEdge()
        setContent {
            SirTheme {
                RadioScreen(
                    modifier = Modifier.fillMaxSize(),
                    castDeviceDetector = castDeviceDetector,
                    castFeatureManager = castFeatureManager,
                    settingsRepository = settingsRepository
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        castDeviceDetector.release()
        castFeatureManager.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    castDeviceDetector: CastDeviceDetector? = null,
    castFeatureManager: CastFeatureManager? = null,
    settingsRepository: SettingsRepository? = null
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    // Settings dialog state
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    // Cast state
    val castDevicesAvailable by castDeviceDetector?.castDevicesAvailable?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val castModuleState by castFeatureManager?.moduleState?.collectAsState()
        ?: remember { mutableStateOf(CastModuleState.NotInstalled) }

    // Auto-download Cast module when devices detected
    LaunchedEffect(castDevicesAvailable, castModuleState) {
        if (castDevicesAvailable && castModuleState is CastModuleState.NotInstalled) {
            val chromecastEnabled = settingsRepository?.chromecastEnabled?.first() ?: false
            if (chromecastEnabled) {
                castFeatureManager?.installCastModule()
            }
        }
    }

    // Predictive back gesture: dismiss settings dialog with system back animation (API 33+)
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    if (inspectionMode) {
        RadioUi(
            modifier = modifier,
            isConnected = true,
            isPlaying = false,
            isBuffering = false,
            showSettingsButton = true,
            onToggle = {},
            onSettingsClick = {}
        )
        return
    }

    val sessionToken = remember {
        SessionToken(
            context,
            ComponentName(context, RadioPlaybackService::class.java)
        )
    }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isConnected by rememberSaveable { mutableStateOf(false) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var isBuffering by rememberSaveable { mutableStateOf(false) }
    var isError by rememberSaveable { mutableStateOf(false) }
    var trackTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var artist by rememberSaveable { mutableStateOf<String?>(null) }

    // Runtime permission requests: POST_NOTIFICATIONS (API 33+), BLUETOOTH_CONNECT (API 31+)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    LaunchedEffect(Unit) {
        val toRequest = listOfNotNull(
            Manifest.permission.POST_NOTIFICATIONS.takeIf {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            },
            Manifest.permission.BLUETOOTH_CONNECT.takeIf {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        )
        toRequest.takeIf { it.isNotEmpty() }?.let { permissionLauncher.launch(it.toTypedArray()) }
    }

    // Metered network warning (one-time per session)
    var showMeteredWarning by rememberSaveable { mutableStateOf(false) }
    var meteredWarningDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!meteredWarningDismissed) {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            if (cm?.isActiveNetworkMetered == true) showMeteredWarning = true
        }
    }

    // Sleep timer countdown label
    val sleepTimerFiresAt by settingsRepository?.sleepTimerFiresAt?.collectAsState(0L)
        ?: remember { mutableStateOf(0L) }
    var sleepTimerLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sleepTimerFiresAt) {
        while (true) {
            val remaining = sleepTimerFiresAt - System.currentTimeMillis()
            sleepTimerLabel = if (remaining > 0)
                context.getString(
                    R.string.sleep_timer_countdown,
                    (remaining / 60_000).toInt().coerceAtLeast(1)
                ) else null
            if (sleepTimerFiresAt <= 0L) break
            delay(30_000L)
        }
    }

    LaunchedEffect(sessionToken) {
        context.ensureRadioServiceRunning()
        try {
            val newController = MediaController.Builder(context, sessionToken)
                .buildAsync()
                .await()
            controller = newController
            isConnected = true
            isPlaying = newController.isActuallyPlaying
            isBuffering = newController.playbackState == Player.STATE_BUFFERING
            // Seed from any metadata already in the session
            newController.mediaMetadata.also {
                trackTitle = it.title?.toString()
                artist = it.artist?.toString()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(RADIO_TAG, "Failed to connect to media session", error)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller?.release()
            controller = null
            isConnected = false
        }
    }

    DisposableEffect(controller) {
        val activeController = controller ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isActuallyPlaying
                isBuffering = player.playbackState == Player.STATE_BUFFERING
                if (player.playbackState == Player.STATE_READY) isError = false
            }

            override fun onPlayerError(error: PlaybackException) {
                isError = true
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                trackTitle = mediaMetadata.title?.toString()
                artist = mediaMetadata.artist?.toString()
                trackTitle?.let { title ->
                    scope.launch { settingsRepository?.addHistoryEntry(title, artist) }
                }
            }
        }
        activeController.addListener(listener)
        onDispose {
            activeController.removeListener(listener)
        }
    }

    RadioUi(
        modifier = modifier,
        isConnected = isConnected,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        isError = isError,
        trackTitle = trackTitle,
        artist = artist,
        sleepTimerLabel = sleepTimerLabel,
        showSettingsButton = true,
        onSettingsClick = { showSettings = true },
        onHistoryClick = { showHistory = true }
    ) {
        val activeController = controller
        if (activeController == null) {
            context.ensureRadioServiceRunning()
            return@RadioUi
        }
        if (isPlaying) {
            activeController.pause()
        } else {
            context.ensureRadioServiceRunning()
            activeController.play()
        }
    }

    // History bottom sheet
    if (showHistory && settingsRepository != null) {
        val history by settingsRepository.nowPlayingHistory.collectAsState(emptyList())
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Text(
                text = stringResource(R.string.history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    history.forEach { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title) },
                            supportingContent = entry.artist?.let {
                                { Text(it) }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Settings dialog
    if (showSettings && settingsRepository != null && castFeatureManager != null) {
        SettingsSheet(
            settingsRepository = settingsRepository,
            castFeatureManager = castFeatureManager,
            onDismiss = { showSettings = false }
        )
    }

    // Metered network warning dialog (shown once per session on cellular)
    if (showMeteredWarning) {
        AlertDialog(
            onDismissRequest = { showMeteredWarning = false; meteredWarningDismissed = true },
            title = { Text(stringResource(R.string.metered_network_title)) },
            text = { Text(stringResource(R.string.metered_network_message)) },
            confirmButton = {
                TextButton(onClick = { showMeteredWarning = false; meteredWarningDismissed = true }) {
                    Text(stringResource(R.string.metered_network_dismiss))
                }
            }
        )
    }
}

// --- Player extension ---

internal val Player.isActuallyPlaying: Boolean
    get() = playWhenReady && playbackState == Player.STATE_READY

// --- Previews ---

@Preview(showBackground = true, name = "Idle")
@Composable
fun RadioScreenPreview() {
    SirTheme {
        RadioUi(
            modifier = Modifier.fillMaxSize(),
            isConnected = true,
            isPlaying = false,
            isBuffering = false,
            showSettingsButton = true,
            onSettingsClick = {},
            onToggle = {}
        )
    }
}

@Preview(showBackground = true, name = "Playing with Metadata")
@Composable
fun RadioScreenPlayingPreview() {
    SirTheme {
        RadioUi(
            modifier = Modifier.fillMaxSize(),
            isConnected = true,
            isPlaying = true,
            isBuffering = false,
            trackTitle = "Sweet Home Alabama",
            artist = "Lynyrd Skynyrd",
            showSettingsButton = true,
            onSettingsClick = {},
            onToggle = {}
        )
    }
}

