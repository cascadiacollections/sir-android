@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cascadiacollections.sir.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import com.cascadiacollections.sir.PlaybackStatus
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.canFavorite
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.StreamFailure
import com.cascadiacollections.sir.displayedFailure
import com.cascadiacollections.sir.status
import com.cascadiacollections.sir.trackLine
import com.cascadiacollections.sir.transportAction
import kotlinx.coroutines.delay

/**
 * The full-screen listen surface, including its own app bar.
 *
 * Kept as the entry point used by previews and screenshot tests; the tabbed shell
 * renders [ListenScreen] directly instead, because the shell owns the scaffold.
 */
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
    station: Station? = null,
    failure: StreamFailure? = null,
    onSettingsClick: () -> Unit = {},
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    Scaffold(
        modifier = modifier,
        topBar = {
            if (showSettingsButton) {
                TopAppBar(
                    title = {},
                    actions = {
                        if (isPlaying && trackTitle != null) {
                            IconButton(onClick = {
                                val shareText = listOfNotNull(trackTitle, artist).joinToString(" — ")
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                resources.getString(R.string.share_now_playing, shareText)
                                            )
                                        },
                                        null
                                    )
                                )
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
        ListenScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = RadioUiState(
                isConnected = isConnected,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isError = isError,
                trackTitle = trackTitle,
                artist = artist,
                sleepTimerLabel = sleepTimerLabel,
                station = station,
                failure = failure
            ),
            onToggle = onToggle
        )
    }
}

/**
 * The listen surface (ShoutKit's Now Playing) without any scaffolding, so it can be dropped
 * into the tabbed shell (which owns the app bar, mini player and navigation bar) as well as
 * into [RadioUi].
 *
 * Station artwork (or the track's album art) over the station name and "Title — Artist",
 * a status badge, the typed failure in ShoutKit's words, and the transport row: favourite,
 * a play button whose action follows the state (Play / Pause / Cancel / Retry), and an
 * overflow holding the one external link, "Open in Apple Music".
 */
@Composable
fun ListenScreen(
    state: RadioUiState,
    modifier: Modifier = Modifier,
    isWideLayout: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onToggle: () -> Unit
) {
    // Widen margins on medium/expanded screens (Pixel 10 Pro Fold, tablets) so the
    // centered content doesn't span the full display width. isWideLayout is derived once
    // from WindowSizeClass at the shell level (SirAppShell) rather than recomputed here.
    val horizontalPadding = if (isWideLayout) 72.dp else 24.dp
    val maxArtworkSize = if (isWideLayout) 280.dp else 240.dp

    val stationName = state.station?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.station_name)
    val trackLine = state.trackLine() ?: stringResource(R.string.track_live_radio)
    val status = state.status
    val failure = state.displayedFailure
    val action = state.transportAction

    Column(modifier = modifier) {
        if (state.isBuffering) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Measured before the scroll container (inside it the height is unbounded), so the
        // artwork shrinks on short windows — landscape, split screen — instead of pushing
        // the transport controls off screen.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val artworkSize = min(maxArtworkSize, maxHeight * ARTWORK_HEIGHT_FRACTION)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NowPlayingArtwork(
                        albumArtUrl = state.albumArtUrl,
                        station = state.station,
                        stationName = stationName,
                        size = artworkSize,
                        shape = RoundedCornerShape(28.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    StatusBadge(status = status, failure = failure)
                    // One accessibility element: "<station>, <track>".
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stationName,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = trackLine,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (failure != null && status == PlaybackStatus.FAILED) {
                        Text(
                            text = stringResource(failure.displayMessageRes(short = false)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    StreamVisualizer(
                        isPlaying = state.isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        FavoriteButton(
                            isFavorite = state.isFavorite,
                            enabled = state.canFavorite,
                            onToggle = onToggleFavorite
                        )
                        LargeFloatingActionButton(
                            onClick = onToggle,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = stringResource(action.labelRes),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        NowPlayingOverflow(trackViewUrl = state.trackViewUrl)
                    }
                    state.sleepTimerLabel?.let { label ->
                        Text(
                            text = label,
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

/** At most this share of the window height goes to the artwork. */
private const val ARTWORK_HEIGHT_FRACTION = 0.4f

/** Connecting… / Reconnecting… / Live / the failure's short text. Nothing while idle. */
@Composable
private fun StatusBadge(status: PlaybackStatus, failure: StreamFailure?) {
    val text = when (status) {
        PlaybackStatus.CONNECTING -> stringResource(R.string.status_connecting)
        PlaybackStatus.RECONNECTING -> stringResource(R.string.stream_reconnecting)
        PlaybackStatus.LIVE -> stringResource(R.string.status_live)
        PlaybackStatus.FAILED -> failure?.let { stringResource(it.displayMessageRes(short = true)) }
        PlaybackStatus.IDLE -> null
    } ?: return
    val (container, content) = when (status) {
        PlaybackStatus.FAILED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        PlaybackStatus.LIVE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

        else ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        // Announce state changes (connecting → live, live → error) without a focus move.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** The favourite toggle for the current station; disabled for the app's own stream. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(if (isFavorite) R.string.favorite_on else R.string.favorite_off)
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        modifier = Modifier.semantics { stateDescription = stateText }
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(R.string.favorite),
            tint = if (isFavorite && enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * The overflow menu. Its only entry is "Open in Apple Music" — ShoutKit's one external link —
 * so the button is only shown when the album-art lookup found the track's page. An empty
 * slot of the same size keeps the play button centred either way.
 */
@Composable
private fun NowPlayingOverflow(trackViewUrl: String?) {
    if (trackViewUrl == null) {
        Spacer(modifier = Modifier.size(48.dp))
        return
    }
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_in_apple_music)) },
                onClick = {
                    expanded = false
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, trackViewUrl.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        // No browser or Music app to handle it; nothing useful to do.
                    }
                }
            )
        }
    }
}

@Composable
internal fun StreamVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
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
            Triple(2.0f, 0.85f, 1.6f)
        )
        bars.forEach { (offset, speed1, speed2) ->
            // Layer two sine waves at different frequencies for organic feel
            val primary = kotlin.math.sin((tick * speed1 + offset).toDouble())
            val secondary = kotlin.math.sin((tick * speed2 + offset * 1.7f).toDouble()) * 0.3
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
