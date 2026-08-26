@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3WindowSizeClassApi::class)

package com.cascadiacollections.sir

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.windowsizeclass.WindowSizeClass
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
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.ui.LicensesScreen
import com.cascadiacollections.sir.ui.RadioUi
import com.cascadiacollections.sir.ui.SirAppShell
import com.cascadiacollections.sir.ui.SirTab
import com.cascadiacollections.sir.ui.theme.SirTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

private const val ACTION_SHORTCUT_PLAY = "com.cascadiacollections.sir.SHORTCUT_PLAY"
private const val DEEP_LINK_SCHEME = "sir"
private const val DEEP_LINK_HOST_STATION = "station"

class MainActivity : ComponentActivity() {

    private val castDeviceDetector by lazy { CastDeviceDetector(this) }
    private val castFeatureManager by lazy { CastFeatureManager(this) }
    // Application context: the repository is captured by long-lived DataStore collectors,
    // so holding the Activity here would leak it for the life of those collectors.
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    // Surfaces a sir://station/{id} deep link into the Compose tree, mirroring how
    // CastDeviceDetector bridges its own StateFlow into RadioScreen via collectAsState().
    private val pendingStationId = MutableStateFlow<String?>(null)

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
        handleDeepLink(intent)

        enableEdgeToEdge()
        setContent {
            SirTheme {
                RadioScreen(
                    modifier = Modifier.fillMaxSize(),
                    windowSizeClass = calculateWindowSizeClass(this),
                    castDeviceDetector = castDeviceDetector,
                    castFeatureManager = castFeatureManager,
                    settingsRepository = settingsRepository,
                    pendingStationId = pendingStationId,
                    onDeepLinkConsumed = { pendingStationId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        castDeviceDetector.release()
        castFeatureManager.release()
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        if (uri.scheme != DEEP_LINK_SCHEME || uri.host != DEEP_LINK_HOST_STATION) return
        val stationId = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return
        pendingStationId.value = stationId
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass,
    castDeviceDetector: CastDeviceDetector? = null,
    castFeatureManager: CastFeatureManager? = null,
    settingsRepository: SettingsRepository? = null,
    pendingStationId: StateFlow<String?> = MutableStateFlow(null),
    onDeepLinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current

    // Shell state
    var selectedTab by rememberSaveable { mutableStateOf(SirTab.LISTEN) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }

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

    // Predictive back gesture: back from any tab returns to listen rather than exiting
    BackHandler(enabled = selectedTab != SirTab.LISTEN) {
        selectedTab = SirTab.LISTEN
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

    val repository = settingsRepository ?: return
    val viewModel: RadioViewModel = viewModel(
        factory = RadioViewModel.Factory(
            application = context.applicationContext as Application,
            settingsRepository = repository
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    // Must go through the ViewModelStore: a ViewModel built with remember never receives
    // onCleared(), so its DataStore collectors would outlive every destroyed Activity.
    val browserViewModel: RadioBrowserViewModel = viewModel(
        factory = RadioBrowserViewModel.Factory(
            directory = AppDirectory.instance,
            settingsRepository = repository
        )
    )

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

    if (castFeatureManager == null) return

    val pendingId by pendingStationId.collectAsState()
    LaunchedEffect(pendingId) {
        val id = pendingId ?: return@LaunchedEffect
        val station = AppDirectory.instance.getStation(id).getOrNull()
            ?: repository.savedStations.first().firstOrNull { it.id == id }
        if (station != null && station.isPlayable) {
            repository.selectStation(station)
            selectedTab = SirTab.LISTEN
        }
        onDeepLinkConsumed()
    }

    SirAppShell(
        modifier = modifier,
        windowSizeClass = windowSizeClass,
        uiState = uiState,
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        onToggle = { viewModel.togglePlayback() },
        browserViewModel = browserViewModel,
        settingsRepository = repository,
        castFeatureManager = castFeatureManager,
        onOpenLicenses = { showLicenses = true }
    )

    // Open Source Licenses. This BackHandler is composed after (and thus takes dispatcher
    // priority over) the tab-reset handler above while the overlay is showing, so back closes
    // Licenses first rather than silently resetting the tab underneath it.
    if (showLicenses) {
        BackHandler(enabled = true) {
            showLicenses = false
        }
        LicensesScreen(onBack = { showLicenses = false })
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
