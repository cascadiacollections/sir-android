package com.cascadiacollections.sir

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cascadiacollections.sir.ui.theme.SirTheme
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await

private const val RADIO_TAG = "RadioScreen"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SirTheme {
                RadioScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun RadioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current

    if (inspectionMode) {
        RadioUi(
            modifier = modifier,
            isConnected = true,
            isPlaying = false,
            isBuffering = false,
            onToggle = {}
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

    LaunchedEffect(sessionToken) {
        context.ensureRadioServiceRunning()
        try {
            val newController = MediaController.Builder(context, sessionToken)
                .buildAsync()
                .await()
            controller = newController
            isConnected = true
            isPlaying = newController.isActuallyPlaying()
            isBuffering = newController.playbackState == Player.STATE_BUFFERING
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
                isPlaying = player.isActuallyPlaying()
                isBuffering = player.playbackState == Player.STATE_BUFFERING
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
        isBuffering = isBuffering
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
}

@Composable
private fun RadioUi(
    modifier: Modifier,
    isConnected: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
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
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val title = when {
                    !isConnected -> "Connecting to radio"
                    isBuffering -> "Buffering willradio"
                    isPlaying -> "Now playing willradio"
                    else -> "Tap anywhere to play"
                }
                val subtitle = when {
                    !isConnected -> "Starting playback service"
                    isBuffering -> "Hang tight, stream is loading"
                    isPlaying -> "Tap again to stop"
                    else -> "Streaming via internet radio"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RadioScreenPreview() {
    SirTheme {
        RadioUi(
            modifier = Modifier.fillMaxSize(),
            isConnected = true,
            isPlaying = false,
            isBuffering = false,
            onToggle = {}
        )
    }
}

private fun Player.isActuallyPlaying(): Boolean {
    return playWhenReady && playbackState == Player.STATE_READY
}

private fun Context.ensureRadioServiceRunning() {
    val intent = Intent(this, RadioPlaybackService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(this, intent)
    } else {
        startService(intent)
    }
}