package com.cascadiacollections.sir.wear

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import kotlinx.coroutines.guava.await

class WearActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }

    @Composable
    private fun WearApp() {
        var controller by remember { mutableStateOf<MediaController?>(null) }
        var isPlaying by remember { mutableStateOf(false) }
        var isBuffering by remember { mutableStateOf(false) }
        var trackTitle by remember { mutableStateOf<String?>(null) }

        DisposableEffect(Unit) {
            val token = SessionToken(
                this@WearActivity,
                ComponentName(this@WearActivity, WearPlaybackService::class.java)
            )
            val future = MediaController.Builder(this@WearActivity, token).buildAsync()
            future.addListener({
                val ctrl = try { future.get() } catch (e: Exception) { return@addListener }
                controller = ctrl
                isPlaying = ctrl.isPlaying
                isBuffering = ctrl.playbackState == Player.STATE_BUFFERING
                trackTitle = ctrl.mediaMetadata.title?.toString()
                ctrl.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                    }
                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        trackTitle = metadata.title?.toString()
                    }
                })
            }, ContextCompat.getMainExecutor(this@WearActivity))

            onDispose {
                controller?.release()
                controller = null
            }
        }

        MaterialTheme {
            AppScaffold {
                ScreenScaffold(
                    timeText = { TimeText() }
                ) {
                    WearPlayerUi(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        trackTitle = trackTitle,
                        onToggle = {
                            controller?.let { ctrl ->
                                if (ctrl.isPlaying) ctrl.pause() else {
                                    ContextCompat.startForegroundService(
                                        this@WearActivity,
                                        android.content.Intent(this@WearActivity, WearPlaybackService::class.java)
                                    )
                                    ctrl.play()
                                }
                            } ?: ContextCompat.startForegroundService(
                                this@WearActivity,
                                android.content.Intent(this@WearActivity, WearPlaybackService::class.java)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun WearPlayerUi(
    isPlaying: Boolean,
    isBuffering: Boolean,
    trackTitle: String?,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.station_name),
                style = MaterialTheme.typography.titleMedium
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
