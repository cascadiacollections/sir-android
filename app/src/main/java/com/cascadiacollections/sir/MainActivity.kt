@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3WindowSizeClassApi::class)

package com.cascadiacollections.sir

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.cascadiacollections.sir.ui.LicensesScreen
import com.cascadiacollections.sir.ui.RadioDestination
import com.cascadiacollections.sir.ui.RadioUi
import com.cascadiacollections.sir.ui.SettingsSheet
import com.cascadiacollections.sir.ui.theme.SirTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val ACTION_SHORTCUT_PLAY = "com.cascadiacollections.sir.SHORTCUT_PLAY"

class MainActivity : ComponentActivity() {

    private val castDeviceDetector by lazy { CastDeviceDetector(this) }
    private val castFeatureManager by lazy { CastFeatureManager(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

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
            val windowSizeClass = calculateWindowSizeClass(this)
            SirTheme {
                RadioScreen(
                    modifier = Modifier.fillMaxSize(),
                    windowWidthSizeClass = windowSizeClass.widthSizeClass,
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
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    castDeviceDetector: CastDeviceDetector? = null,
    castFeatureManager: CastFeatureManager? = null,
    settingsRepository: SettingsRepository? = null
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current

    // Overlay destination state
    var destination by rememberSaveable { mutableStateOf(RadioDestination.None) }

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

    // Predictive back gesture: dismiss the active overlay (Settings or Licenses) with the
    // system back animation.
    PredictiveBackHandler(enabled = destination != RadioDestination.None) { progress ->
        try {
            progress.collect { }
            destination = RadioDestination.None
        } catch (e: CancellationException) {
            // Gesture cancelled — leave the overlay open.
        }
    }

    if (inspectionMode) {
        RadioUi(
            modifier = modifier,
            windowWidthSizeClass = windowWidthSizeClass,
            isConnected = true,
            isPlaying = false,
            isBuffering = false,
            showSettingsButton = true,
            onToggle = {},
            onSettingsClick = {}
        )
        return
    }

    val viewModel: RadioViewModel = viewModel(
        factory = RadioViewModel.Factory(
            application = context.applicationContext as Application,
            settingsRepository = settingsRepository ?: return
        )
    )
    val uiState by viewModel.uiState.collectAsState()

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

    RadioUi(
        modifier = modifier,
        windowWidthSizeClass = windowWidthSizeClass,
        isConnected = uiState.isConnected,
        isPlaying = uiState.isPlaying,
        isBuffering = uiState.isBuffering,
        isError = uiState.isError,
        trackTitle = uiState.trackTitle,
        artist = uiState.artist,
        sleepTimerLabel = uiState.sleepTimerLabel,
        showSettingsButton = true,
        onSettingsClick = { destination = RadioDestination.Settings },
        onToggle = { viewModel.togglePlayback() }
    )

    // Settings dialog
    if (destination == RadioDestination.Settings && castFeatureManager != null) {
        SettingsSheet(
            settingsRepository = settingsRepository,
            castFeatureManager = castFeatureManager,
            onDismiss = { destination = RadioDestination.None },
            onOpenLicenses = { destination = RadioDestination.Licenses }
        )
    }

    // Open Source Licenses
    if (destination == RadioDestination.Licenses) {
        LicensesScreen(onBack = { destination = RadioDestination.None })
    }

    // Metered network warning dialog (shown once per session on cellular)
    if (uiState.showMeteredWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissMeteredWarning() },
            title = { Text(stringResource(R.string.metered_network_title)) },
            text = { Text(stringResource(R.string.metered_network_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMeteredWarning() }) {
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
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
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
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
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

@Preview(showBackground = true, name = "Medium width (e.g. unfolded phone)", widthDp = 700, heightDp = 900)
@Composable
fun RadioScreenMediumWidthPreview() {
    SirTheme {
        RadioUi(
            modifier = Modifier.fillMaxSize(),
            windowWidthSizeClass = WindowWidthSizeClass.Medium,
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

@Preview(showBackground = true, name = "Expanded width (e.g. tablet)", widthDp = 1000, heightDp = 900)
@Composable
fun RadioScreenExpandedWidthPreview() {
    SirTheme {
        RadioUi(
            modifier = Modifier.fillMaxSize(),
            windowWidthSizeClass = WindowWidthSizeClass.Expanded,
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
