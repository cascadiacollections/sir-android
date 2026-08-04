package com.cascadiacollections.sir.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.core.model.Station

/**
 * A single station row, shared by the browse and library tabs.
 *
 * Tapping anywhere on the row starts playback, which leaves the trailing slot free for
 * the list-specific action (save, remove, ...).
 */
@Composable
fun StationRow(
    station: Station,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = modifier.clickable(enabled = station.isPlayable, onClick = onPlay),
        headlineContent = { Text(station.name) },
        supportingContent = {
            Text(
                if (isPlaying) stringResource(R.string.station_now_playing) else station.displayLabel
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.play_station),
                tint = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        trailingContent = trailing
    )
}
