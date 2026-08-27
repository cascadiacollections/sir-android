package com.cascadiacollections.sir.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.core.model.Station

/**
 * A single station row, shared by the browse and library tabs.
 *
 * Tapping anywhere on the row starts playback, which leaves the trailing slot free for
 * the list-specific action (save, remove, ...). [onLongClick] is opt-in per call site
 * (e.g. the library's saved-stations section wires it to open the edit sheet) since
 * long-press has no meaning for browse results or recents.
 */
@Composable
fun StationRow(
    station: Station,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = modifier.combinedClickable(
            enabled = station.isPlayable,
            onClick = onPlay,
            onLongClick = onLongClick
        ),
        headlineContent = { Text(station.name) },
        supportingContent = {
            Text(
                if (isPlaying) stringResource(R.string.station_now_playing) else station.displayLabel
            )
        },
        leadingContent = {
            StationArtwork(
                station = station,
                isPlaying = isPlaying,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = trailing
    )
}

/**
 * The station's directory-provided artwork (a radio-browser favicon URL), falling back
 * to the same play icon used before artwork existed — for stations with no favicon, and
 * for a favicon URL that fails to load (dead link, unsupported format, offline).
 */
@Composable
private fun StationArtwork(
    station: Station,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var loadFailed by remember(station.favicon) { mutableStateOf(false) }
    val tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    if (station.favicon.isNullOrBlank() || loadFailed) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.play_station),
            tint = tint,
            modifier = modifier
        )
    } else {
        AsyncImage(
            model = station.favicon,
            contentDescription = stringResource(R.string.play_station),
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true },
            modifier = modifier.clip(CircleShape)
        )
    }
}
