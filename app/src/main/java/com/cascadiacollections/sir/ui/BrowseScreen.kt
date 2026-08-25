@file:OptIn(ExperimentalMaterial3Api::class)

package com.cascadiacollections.sir.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioBrowserViewModel

/**
 * Station discovery: search the directory and add results to the library.
 *
 * Saved and recently-played stations deliberately live on the library tab instead of
 * being repeated here — this screen is only about finding something new.
 */
@Composable
fun BrowseScreen(
    viewModel: RadioBrowserViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.search_stations_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_search)
                            )
                        }
                    }
                }
            )
            FilledIconButton(
                onClick = { viewModel.search() },
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }
            }
        }

        uiState.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (uiState.searchResults.isEmpty() && !uiState.isLoading) {
            Text(
                text = stringResource(R.string.browse_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.searchResults) { station ->
                val isSaved = viewModel.isStationSaved(station)
                StationRow(
                    station = station,
                    isPlaying = station.id == uiState.selectedStationId,
                    onPlay = { viewModel.playStation(station) },
                    trailing = {
                        IconButton(
                            onClick = {
                                if (isSaved) {
                                    viewModel.removeStation(station.id)
                                    Toast.makeText(
                                        context,
                                        resources.getString(R.string.station_removed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    viewModel.saveStation(station)
                                    Toast.makeText(
                                        context,
                                        resources.getString(R.string.station_saved),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            if (isSaved) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.station_saved)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.save_station)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
