@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cascadiacollections.sir.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioUi(
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
    onHistoryClick: () -> Unit = {},
    onToggle: () -> Unit
) {
    // Widen margins on medium/expanded screens (Pixel 10 Pro Fold, tablets) so the
    // centered content doesn't span the full display width at ≥ 600dp.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = if (screenWidthDp >= 600) 72.dp else 24.dp

    val title = when {
        !isConnected           -> stringResource(R.string.title_connecting)
        isError && isBuffering -> stringResource(R.string.stream_reconnecting)
        isError                -> stringResource(R.string.title_stream_error)
        isBuffering            -> stringResource(R.string.title_buffering)
        isPlaying              -> trackTitle?.takeIf { it.isNotBlank() }
                                      ?: stringResource(R.string.now_playing)
        else                   -> stringResource(R.string.station_name)
    }
    val subtitle = when {
        !isConnected           -> stringResource(R.string.subtitle_connecting)
        isError && isBuffering -> stringResource(R.string.subtitle_reconnecting)
        isError                -> stringResource(R.string.stream_error)
        isBuffering            -> stringResource(R.string.subtitle_buffering)
        isPlaying              -> artist?.takeIf { it.isNotBlank() }
                                      ?: stringResource(R.string.subtitle_live)
        else                   -> stringResource(R.string.subtitle_idle)
    }

    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            if (showSettingsButton) {
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(onClick = onHistoryClick) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = stringResource(R.string.history),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isPlaying && trackTitle != null) {
                            IconButton(onClick = {
                                val shareText = listOfNotNull(trackTitle, artist).joinToString(" — ")
                                context.startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT,
                                            context.getString(R.string.share_now_playing, shareText))
                                    }, null
                                ))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isBuffering) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    Spacer(modifier = Modifier.height(16.dp))
                    StreamVisualizer(
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    LargeFloatingActionButton(
                        onClick = onToggle,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            modifier = Modifier.size(36.dp)
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
}

@Composable
internal fun StreamVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primaryContainer

    var tick by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                tick += 0.035f
                delay(50L)
            }
        } else {
            tick = 0f
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Each bar: phase offset, primary speed, secondary speed (layered sine for less predictable motion)
        val bars = listOf(
            Triple(0.0f, 0.7f, 1.9f),
            Triple(0.9f, 0.5f, 2.3f),
            Triple(1.8f, 0.8f, 1.7f),
            Triple(0.5f, 0.6f, 2.1f),
            Triple(2.4f, 0.9f, 1.5f),
            Triple(1.3f, 0.55f, 2.5f),
            Triple(3.1f, 0.75f, 1.8f),
            Triple(0.3f, 0.65f, 2.2f),
            Triple(2.0f, 0.85f, 1.6f),
        )
        bars.forEach { (offset, speed1, speed2) ->
            // Layer two sine waves at different frequencies for organic feel
            val primary = kotlin.math.sin((tick * speed1 + offset).toDouble())
            val secondary = kotlin.math.sin((tick * speed2 + offset * 1.7).toDouble()) * 0.3
            val h = ((primary + secondary + 1.3) / 2.6)
                .toFloat()
                .coerceIn(0.15f, 0.85f)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction = h)
                    .background(
                        barColor,
                        RoundedCornerShape(topStartPercent = 50, topEndPercent = 50)
                    )
            )
        }
    }
}

