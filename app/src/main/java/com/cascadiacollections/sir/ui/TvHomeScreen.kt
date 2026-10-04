package com.cascadiacollections.sir.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.TransportAction
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.genreLabel
import com.cascadiacollections.sir.trackLine
import com.cascadiacollections.sir.transportAction

/** The Popular shelf's load state. */
sealed interface TvPopular {
    data object Loading : TvPopular
    data object Failed : TvPopular
    data class Loaded(val stations: List<Station>) : TvPopular
}

/**
 * ShoutKit's tvOS screen for Android TV: a now-playing banner over horizontal station
 * shelves. Built for the D-pad rather than adapted from the phone — every control is a
 * focusable button or card, shelves scroll sideways and up/down moves between them — so
 * a remote reaches everything and focus is always visible.
 */
@Composable
fun TvHomeScreen(
    radio: RadioUiState,
    recents: List<Station>,
    popular: TvPopular,
    onStationSelected: (Station) -> Unit,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    onRetryPopular: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            item(key = "now-playing") {
                NowPlayingBanner(radio, onTogglePlayback, onStop)
            }
            if (recents.isNotEmpty()) {
                item(key = "recents") {
                    Shelf(stringResource(R.string.recent_stations), recents, radio.station?.id, onStationSelected)
                }
            }
            item(key = "popular") {
                val title = stringResource(R.string.popular_stations)
                when (popular) {
                    TvPopular.Loading -> ShelfMessage(title, stringResource(R.string.tv_loading_stations))
                    TvPopular.Failed -> ShelfMessage(title, stringResource(R.string.tv_stations_unavailable)) {
                        OutlinedButton(onClick = onRetryPopular, modifier = Modifier.focusScale()) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                    is TvPopular.Loaded ->
                        if (popular.stations.isEmpty()) {
                            ShelfMessage(title, stringResource(R.string.tv_no_stations))
                        } else {
                            Shelf(title, popular.stations, radio.station?.id, onStationSelected)
                        }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBanner(radio: RadioUiState, onTogglePlayback: () -> Unit, onStop: () -> Unit) {
    val stationName = radio.station?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.station_name)
    val playFocus = remember { FocusRequester() }
    // Land on the transport so OK plays straight away, as the tvOS banner does.
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Top) {
        StationArtworkImage(station = radio.station, stationName = stationName, size = 180.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            Text(
                stationName,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                radio.trackLine() ?: stringResource(R.string.tv_pick_a_station),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                val action = radio.transportAction
                Button(onClick = onTogglePlayback, modifier = Modifier.focusRequester(playFocus).focusScale()) {
                    Icon(
                        when (action) {
                            TransportAction.PAUSE -> Icons.Default.Pause
                            TransportAction.RETRY -> Icons.Default.Refresh
                            TransportAction.CANCEL -> Icons.Default.Stop
                            TransportAction.PLAY -> Icons.Default.PlayArrow
                        },
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            when (action) {
                                TransportAction.PAUSE -> R.string.pause
                                TransportAction.RETRY -> R.string.retry
                                TransportAction.CANCEL -> R.string.tv_stop
                                TransportAction.PLAY -> R.string.play
                            }
                        )
                    )
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.focusScale()) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tv_stop))
                }
            }
        }
    }
}

@Composable
private fun Shelf(title: String, stations: List<Station>, currentId: String?, onSelect: (Station) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            // Room for the focused card to scale without clipping at the edges.
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
        ) {
            items(stations, key = { it.id.ifBlank { it.url } }) { station ->
                StationCard(station, isCurrent = station.id == currentId, onClick = { onSelect(station) })
            }
        }
    }
}

@Composable
private fun StationCard(station: Station, isCurrent: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        border = when {
            focused -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            isCurrent -> BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            else -> null
        },
        modifier = Modifier
            .width(CARD_WIDTH)
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) FOCUSED_SCALE else 1f)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StationArtworkImage(station = station, stationName = station.name, size = CARD_WIDTH - 24.dp)
            Text(station.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                station.genreLabel().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShelfMessage(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

/**
 * Grows a focused control and rings it, so the remote's focus is obvious from the couch —
 * a Material button's own focus overlay is too faint at TV distance.
 */
@Composable
private fun Modifier.focusScale(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    return onFocusChanged { focused = it.isFocused }
        .scale(if (focused) FOCUSED_SCALE else 1f)
        .then(if (focused) Modifier.border(3.dp, ring, CircleShape) else Modifier)
}

private val CARD_WIDTH = 220.dp
private const val FOCUSED_SCALE = 1.08f
