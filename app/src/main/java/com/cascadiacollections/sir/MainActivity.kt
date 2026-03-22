package com.cascadiacollections.sir

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cascadiacollections.sir.ui.theme.SirTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val RADIO_TAG = "RadioScreen"

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

@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    castDeviceDetector: CastDeviceDetector? = null,
    castFeatureManager: CastFeatureManager? = null,
    settingsRepository: SettingsRepository? = null
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current

    // Settings dialog state
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Cast state
    val castDevicesAvailable by castDeviceDetector?.castDevicesAvailable?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val castModuleState by castFeatureManager?.moduleState?.collectAsState()
        ?: remember { mutableStateOf(CastModuleState.NotInstalled) }

    // Auto-download Cast module when devices detected
    LaunchedEffect(castDevicesAvailable, castModuleState) {
        if (castDevicesAvailable && castModuleState is CastModuleState.NotInstalled) {
            // Check if user has enabled Chromecast in settings
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
        onSettingsClick = { showSettings = true }
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

    // Settings dialog
    if (showSettings && settingsRepository != null && castFeatureManager != null) {
        SettingsDialog(
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
            text  = { Text(stringResource(R.string.metered_network_message)) },
            confirmButton = {
                TextButton(onClick = { showMeteredWarning = false; meteredWarningDismissed = true }) {
                    Text(stringResource(R.string.metered_network_dismiss))
                }
            }
        )
    }
}

@Composable
private fun RadioUi(
    modifier: Modifier,
    isConnected: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isError: Boolean = false,
    trackTitle: String? = null,
    artist: String? = null,
    sleepTimerLabel: String? = null,
    showSettingsButton: Boolean = false,
    onSettingsClick: () -> Unit = {},
    onToggle: () -> Unit
) {
    // Widen margins on medium/expanded screens (Pixel 10 Pro Fold, tablets) so the
    // centered content doesn't span the full display width at ≥ 600dp.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = if (screenWidthDp >= 600) 72.dp else 24.dp

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 24.dp)
        ) {
            // Settings button in top right
            if (showSettingsButton) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Show track title if available, otherwise show status
                val title = when {
                    !isConnected          -> stringResource(R.string.title_connecting)
                    isError && isBuffering -> stringResource(R.string.stream_reconnecting)
                    isError               -> stringResource(R.string.title_stream_error)
                    isBuffering           -> stringResource(R.string.title_buffering)
                    isPlaying             -> trackTitle?.takeIf { it.isNotBlank() }
                                                ?: stringResource(R.string.now_playing)
                    else                  -> stringResource(R.string.tap_to_play)
                }
                // Show artist if available, otherwise show contextual info
                val subtitle = when {
                    !isConnected          -> stringResource(R.string.subtitle_connecting)
                    isError && isBuffering -> stringResource(R.string.subtitle_reconnecting)
                    isError               -> stringResource(R.string.stream_error)
                    isBuffering           -> stringResource(R.string.subtitle_buffering)
                    isPlaying             -> artist?.takeIf { it.isNotBlank() }
                                                ?: stringResource(R.string.subtitle_live)
                    else                  -> stringResource(R.string.subtitle_idle)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
                // Show tap hint when playing
                if (isPlaying) {
                    Text(
                        text = stringResource(R.string.tap_to_stop),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sleepTimerLabel != null) {
                    Text(
                        text = sleepTimerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    settingsRepository: SettingsRepository,
    castFeatureManager: CastFeatureManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chromecastEnabled by settingsRepository.chromecastEnabled.collectAsState(initial = false)
    val castModuleState by castFeatureManager.moduleState.collectAsState()
    val sleepTimerDuration by settingsRepository.sleepTimerDuration.collectAsState(initial = SleepTimerDuration.OFF)
    val equalizerPreset by settingsRepository.equalizerPreset.collectAsState(initial = EqualizerPreset.NORMAL)
    val customStreamUrl by settingsRepository.customStreamUrl.collectAsState(initial = null)

    // Dropdown expansion states
    var sleepTimerExpanded by remember { mutableStateOf(false) }
    var equalizerExpanded by remember { mutableStateOf(false) }
    var customStreamText by remember { mutableStateOf(customStreamUrl ?: "") }

    // Update custom stream text when flow changes
    LaunchedEffect(customStreamUrl) {
        customStreamText = customStreamUrl ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Sleep Timer dropdown
                Text(
                    text = stringResource(R.string.sleep_timer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = sleepTimerExpanded,
                    onExpandedChange = { sleepTimerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = sleepTimerDuration.label,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sleepTimerExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = sleepTimerExpanded,
                        onDismissRequest = { sleepTimerExpanded = false }
                    ) {
                        SleepTimerDuration.entries.forEach { duration ->
                            DropdownMenuItem(
                                text = { Text(duration.label) },
                                onClick = {
                                    sleepTimerExpanded = false
                                    scope.launch {
                                        settingsRepository.setSleepTimerDuration(duration)
                                        // Send to service
                                        context.startService(
                                            Intent(
                                                context,
                                                RadioPlaybackService::class.java
                                            ).apply {
                                                action = RadioPlaybackService.ACTION_SET_SLEEP_TIMER
                                                putExtra(
                                                    RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES,
                                                    duration.minutes
                                                )
                                            }
                                        )
                                        // Show toast
                                        val message = if (duration == SleepTimerDuration.OFF) {
                                            context.getString(R.string.sleep_timer_off)
                                        } else {
                                            context.getString(
                                                R.string.sleep_timer_set,
                                                duration.label
                                            )
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Equalizer dropdown
                Text(
                    text = stringResource(R.string.equalizer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = equalizerExpanded,
                    onExpandedChange = { equalizerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = equalizerPreset.label,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = equalizerExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = equalizerExpanded,
                        onDismissRequest = { equalizerExpanded = false }
                    ) {
                        EqualizerPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    equalizerExpanded = false
                                    scope.launch {
                                        settingsRepository.setEqualizerPreset(preset)
                                        // Send to service
                                        context.startService(
                                            Intent(
                                                context,
                                                RadioPlaybackService::class.java
                                            ).apply {
                                                action = RadioPlaybackService.ACTION_SET_EQUALIZER
                                                putExtra(
                                                    RadioPlaybackService.EXTRA_EQUALIZER_PRESET,
                                                    preset.ordinal
                                                )
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Chromecast toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enable_chromecast),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        // Show status
                        val statusText = when (castModuleState) {
                            is CastModuleState.Installed -> stringResource(R.string.chromecast_enabled)
                            is CastModuleState.Installing -> {
                                val progress =
                                    (castModuleState as CastModuleState.Installing).progress
                                "${stringResource(R.string.chromecast_downloading)} ${(progress * 100).toInt()}%"
                            }

                            is CastModuleState.Failed -> stringResource(R.string.chromecast_not_available)
                            else -> null
                        }
                        if (statusText != null) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    when (castModuleState) {
                        is CastModuleState.Installing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }

                        is CastModuleState.Installed -> {
                            Switch(
                                checked = true,
                                onCheckedChange = null,
                                enabled = false
                            )
                        }

                        else -> {
                            Switch(
                                checked = chromecastEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        settingsRepository.setChromecastEnabled(enabled)
                                        if (enabled) {
                                            castFeatureManager.installCastModule()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Debug-only: Custom Stream URL
                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.debug_options),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = stringResource(R.string.custom_stream_url),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = customStreamText,
                        onValueChange = { customStreamText = it },
                        placeholder = { Text(stringResource(R.string.custom_stream_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customStreamUrl != null) {
                        Text(
                            text = stringResource(R.string.custom_stream_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    settingsRepository.setCustomStreamUrl(null)
                                    customStreamText = ""
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.custom_stream_reset),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = customStreamUrl != null
                        ) {
                            Text(stringResource(R.string.reset_to_default))
                        }
                        TextButton(
                            onClick = {
                                if (customStreamText.startsWith("http://") || customStreamText.startsWith(
                                        "https://"
                                    )
                                ) {
                                    scope.launch {
                                        settingsRepository.setCustomStreamUrl(customStreamText)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.custom_stream_saved),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.custom_stream_invalid),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = customStreamText.isNotBlank() && customStreamText != customStreamUrl
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}

private val Player.isActuallyPlaying: Boolean
    get() = playWhenReady && playbackState == Player.STATE_READY

private fun Context.ensureRadioServiceRunning() {
    val intent = Intent(this, RadioPlaybackService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(this, intent)
    } else {
        startService(intent)
    }
}
