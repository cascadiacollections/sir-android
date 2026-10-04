package com.cascadiacollections.sir.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.PlaybackStatus
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.displayedFailure
import com.cascadiacollections.sir.status
import com.cascadiacollections.sir.trackLine
import com.cascadiacollections.sir.transportAction

/**
 * Compact now-playing bar shown above the navigation bar on every tab except listen,
 * so playback stays reachable while browsing.
 *
 * Always mounted at a fixed height (rather than conditionally composed) so switching tabs
 * never shifts the navigation chrome above it. Until the controller connects the row is an
 * idle placeholder; interactivity is gated on that, not on whether a track is known — a
 * stream can be actively playing with no ICY title.
 *
 * Shows the station's artwork (or the track's album art) and name over the track line, or
 * the short form of a failure. Tapping the bar returns to the listen tab; only the
 * transport button is a separate target, which keeps the touch target for "go back to what
 * I'm hearing" large.
 */
@Composable
fun MiniPlayer(state: RadioUiState, onToggle: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isIdle = !state.isConnected
    val stationName = state.station?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.station_name)
    val failure = state.displayedFailure
    val subtitle = when (state.status) {
        PlaybackStatus.FAILED -> failure?.let { stringResource(it.displayMessageRes(short = true)) }
        PlaybackStatus.RECONNECTING -> stringResource(R.string.stream_reconnecting)
        else -> null
    } ?: state.trackLine() ?: stringResource(R.string.track_live_radio)
    val action = state.transportAction

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column {
            if (state.isBuffering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier
                    .clickable(onClick = onClick, enabled = !isIdle)
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NowPlayingArtwork(
                    albumArtUrl = state.albumArtUrl,
                    station = state.station,
                    stationName = stationName,
                    size = 40.dp,
                    shape = RoundedCornerShape(8.dp)
                )
                // One accessibility element: "<station>, <track or status>".
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .semantics(mergeDescendants = true) {}
                ) {
                    Text(
                        text = stationName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isIdle) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.status == PlaybackStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggle, enabled = !isIdle) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = stringResource(action.labelRes)
                    )
                }
            }
        }
    }
}
