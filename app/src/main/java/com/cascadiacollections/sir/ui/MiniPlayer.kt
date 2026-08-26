package com.cascadiacollections.sir.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R

/**
 * Compact now-playing bar shown above the navigation bar on every tab except listen,
 * so playback stays reachable while browsing.
 *
 * Always mounted at a fixed height (rather than conditionally composed) so switching tabs
 * never shifts the navigation chrome above it. When [title] is null (nothing resolved to
 * play yet) an idle placeholder row renders instead of the live now-playing row, keeping
 * the same height either way. [title] being null only reflects missing display metadata —
 * a stream can be actively playing with no ICY title — so interactivity is gated on
 * [isIdle] instead, not on [title]'s nullability.
 *
 * Tapping the bar returns to the listen tab; only the play/pause button is a separate
 * target, which keeps the touch target for "go back to what I'm hearing" large.
 */
@Composable
fun MiniPlayer(
    isPlaying: Boolean,
    isBuffering: Boolean,
    isIdle: Boolean,
    title: String?,
    subtitle: String?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column {
            if (isBuffering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier
                    .clickable(onClick = onClick, enabled = !isIdle)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title ?: stringResource(R.string.station_name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isIdle) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onToggle, enabled = !isIdle) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.pause else R.string.play
                        )
                    )
                }
            }
        }
    }
}
