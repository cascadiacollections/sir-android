package com.cascadiacollections.sir.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioBrowserViewModel

/**
 * Saved stations and listening history.
 *
 * Both lists live in one scrolling list rather than two nested lazy lists, so the
 * whole tab scrolls as a single surface.
 */
@Composable
fun LibraryScreen(
    viewModel: RadioBrowserViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.savedStations.isEmpty() && uiState.recentStations.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (uiState.savedStations.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.saved_stations))
            }
            items(uiState.savedStations, key = { "saved-${it.id}" }) { station ->
                StationRow(
                    station = station,
                    isPlaying = station.id == uiState.selectedStationId,
                    onPlay = { viewModel.playStation(station) },
                    trailing = {
                        IconButton(
                            onClick = {
                                viewModel.removeStation(station.id)
                                Toast.makeText(
                                    context,
                                    resources.getString(R.string.station_removed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove_station)
                            )
                        }
                    }
                )
            }
        }

        if (uiState.recentStations.isNotEmpty()) {
            item {
                HorizontalDivider()
                SectionHeader(stringResource(R.string.recent_stations))
            }
            items(uiState.recentStations, key = { "recent-${it.id}" }) { station ->
                StationRow(
                    station = station,
                    isPlaying = station.id == uiState.selectedStationId,
                    onPlay = { viewModel.playStation(station) }
                )
            }
            item {
                TextButton(
                    onClick = { viewModel.clearRecentStations() },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.clear_recent_stations))
                }
            }
        }

        // Escape hatch back to the app's own stream
        if (uiState.selectedStationId != null) {
            item {
                HorizontalDivider()
                TextButton(
                    onClick = { viewModel.playDefaultStream() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.play_default_stream))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}
