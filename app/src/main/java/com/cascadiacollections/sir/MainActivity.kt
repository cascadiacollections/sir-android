package com.cascadiacollections.sir

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cascadiacollections.sir.ui.theme.SirTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val RADIO_TAG = "RadioScreen"

class MainActivity : ComponentActivity() {

    private lateinit var castDeviceDetector: CastDeviceDetector
    private lateinit var castFeatureManager: CastFeatureManager
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
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
    var trackTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var artist by rememberSaveable { mutableStateOf<String?>(null) }

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
            // Get initial metadata if available
            newController.mediaMetadata.let { metadata ->
                trackTitle = metadata.title?.toString()
                artist = metadata.artist?.toString()
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
        trackTitle = trackTitle,
        artist = artist,
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
}

@Composable
private fun RadioUi(
    modifier: Modifier,
    isConnected: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    trackTitle: String? = null,
    artist: String? = null,
    showSettingsButton: Boolean = false,
    onSettingsClick: () -> Unit = {},
    onToggle: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
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
                    !isConnected -> "Connecting to SIR"
                    isBuffering -> "Buffering..."
                    isPlaying -> trackTitle?.takeIf { it.isNotBlank() } ?: "Now playing"
                    else -> "Tap anywhere to play"
                }
                // Show artist if available, otherwise show contextual info
                val subtitle = when {
                    !isConnected -> "Starting playback service"
                    isBuffering -> "Hang tight, stream is loading"
                    isPlaying -> artist?.takeIf { it.isNotBlank() } ?: "SIR • Live"
                    else -> "SIR • Internet radio"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                // Show tap hint when playing
                if (isPlaying) {
                    Text(
                        text = "Tap to stop",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 24.dp),
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                        text = "Debug Options",
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
                                        "Reset to default stream",
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
                                            "Custom stream saved. Restart app to apply.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "URL must start with http:// or https://",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = customStreamText.isNotBlank() && customStreamText != customStreamUrl
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
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
