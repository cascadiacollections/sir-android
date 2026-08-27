package com.cascadiacollections.sir.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.PlaylistImportResult
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioBrowserViewModel
import com.cascadiacollections.sir.core.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var editingStation by remember { mutableStateOf<Station?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = readPlaylistText(context, uri)
            if (text == null) {
                Toast.makeText(context, resources.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            viewModel.importPlaylist(text = text, isPls = isPlsUri(context, uri)) { result ->
                val message = when (result) {
                    is PlaylistImportResult.Empty -> resources.getString(R.string.import_empty)
                    is PlaylistImportResult.Imported -> resources.getString(
                        R.string.import_result,
                        result.added,
                        result.skipped
                    )
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpegurl")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = writePlaylistText(context, uri, viewModel.exportPlaylist())
            Toast.makeText(
                context,
                resources.getString(if (written) R.string.export_success else R.string.export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    editingStation?.let { station ->
        EditStationSheet(
            station = station,
            onDismiss = { editingStation = null },
            onSave = { updated ->
                viewModel.saveStation(updated)
                editingStation = null
                Toast.makeText(context, resources.getString(R.string.station_updated), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (uiState.savedStations.isEmpty() && uiState.recentStations.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            ImportExportRow(
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onExport = {},
                exportEnabled = false
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (uiState.savedStations.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.saved_stations))
                ImportExportRow(
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = { exportLauncher.launch("sir_stations.m3u") },
                    exportEnabled = uiState.savedStations.isNotEmpty()
                )
            }
            items(uiState.savedStations, key = { "saved-${it.id}" }) { station ->
                StationRow(
                    station = station,
                    isPlaying = station.id == uiState.selectedStationId,
                    onPlay = { viewModel.playStation(station) },
                    onLongClick = { editingStation = station },
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

@Composable
private fun ImportExportRow(
    onImport: () -> Unit,
    onExport: () -> Unit,
    exportEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onImport) {
            Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.import_stations))
        }
        IconButton(onClick = onExport, enabled = exportEnabled) {
            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.export_stations))
        }
    }
}

/** Reads the document at [uri] as text, or null if it couldn't be opened/read. */
private suspend fun readPlaylistText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()
}

/** Writes [text] to the document at [uri]; returns whether the write succeeded. */
private suspend fun writePlaylistText(context: Context, uri: Uri, text: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
        }.isSuccess
    }

/**
 * Whether [uri] looks like a PLS playlist rather than M3U, based on its display name.
 * Content pickers rarely report a trustworthy MIME type for playlist files, so the
 * file extension is the more reliable signal.
 */
private fun isPlsUri(context: Context, uri: Uri): Boolean {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment
    return name?.endsWith(".pls", ignoreCase = true) == true
}
