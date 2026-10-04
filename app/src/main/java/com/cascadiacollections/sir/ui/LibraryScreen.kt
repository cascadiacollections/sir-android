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
import com.cascadiacollections.sir.core.persistence.StationCollections
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Badge
import androidx.compose.material3.ListItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.cascadiacollections.sir.core.persistence.FavoritesBackupCodec
import com.cascadiacollections.sir.core.persistence.TopTracks
import com.cascadiacollections.sir.core.persistence.TopTracksTimeframe
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saved stations, listening history, Top Tracks and the Recently Heard drill-in.
 *
 * Everything lives in one scrolling list rather than nested lazy lists, so the whole tab
 * scrolls as a single surface. Saved stations can be reordered by dragging in reorder mode
 * (or with the "Move up"/"Move down" accessibility actions at any time); the dragged order
 * is held locally and persisted once, when the drag ends.
 */
@Composable
fun LibraryScreen(
    viewModel: RadioBrowserViewModel,
    modifier: Modifier = Modifier,
    onOpenRecentlyHeard: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var editingStation by remember { mutableStateOf<Station?>(null) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var topTracksTimeframe by rememberSaveable { mutableStateOf(TopTracksTimeframe.WEEK) }

    // The order shown while dragging. Re-seeded whenever the persisted list changes, which
    // (being a data-class list) only happens on a real change, not on unrelated writes.
    var savedOrder by remember(uiState.savedStations) { mutableStateOf(uiState.savedStations) }
    val topTracks = remember(uiState.heardTracks, topTracksTimeframe) {
        TopTracks.rank(uiState.heardTracks, topTracksTimeframe, nowMillis = System.currentTimeMillis())
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = savedOrder.indexOfFirst { savedKey(it) == from.key }
        val toIndex = savedOrder.indexOfFirst { savedKey(it) == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            savedOrder = savedOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    val showImportResult: (PlaylistImportResult) -> Unit = { result ->
        val message = when (result) {
            is PlaylistImportResult.Empty -> resources.getString(R.string.import_empty)
            is PlaylistImportResult.Unreadable -> resources.getString(R.string.import_unreadable)
            is PlaylistImportResult.Imported -> resources.getString(
                R.string.import_result,
                result.added,
                result.skipped
            )
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

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
            viewModel.importStations(text = text, fileName = displayName(context, uri), onResult = showImportResult)
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

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FavoritesBackupCodec.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = writePlaylistText(context, uri, viewModel.exportFavoritesBackup())
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

    if (uiState.savedStations.isEmpty() && uiState.recentStations.isEmpty() && uiState.heardTracks.isEmpty()) {
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
                onExportBackup = {},
                exportEnabled = false
            )
            HorizontalDivider()
            RecentlyHeardRow(count = 0, onClick = onOpenRecentlyHeard)
        }
        return
    }

    LazyColumn(state = lazyListState, modifier = modifier.fillMaxSize()) {
        if (savedOrder.isNotEmpty()) {
            item(key = "saved-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.saved_stations), Modifier.weight(1f))
                    if (savedOrder.size > 1) {
                        TextButton(
                            onClick = { reordering = !reordering },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(if (reordering) R.string.done else R.string.reorder_stations))
                        }
                    }
                }
                ImportExportRow(
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = { exportLauncher.launch("sir_stations.m3u") },
                    onExportBackup = { backupLauncher.launch(FavoritesBackupCodec.DEFAULT_FILE_NAME) },
                    exportEnabled = true
                )
            }
            itemsIndexed(savedOrder, key = { _, station -> savedKey(station) }) { index, station ->
                ReorderableItem(reorderState, key = savedKey(station), enabled = reordering) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                    val moveUp = stringResource(R.string.move_up)
                    val moveDown = stringResource(R.string.move_down)
                    Surface(shadowElevation = elevation) {
                        StationRow(
                            station = station,
                            isPlaying = station.id == uiState.selectedStationId,
                            onPlay = { viewModel.playStation(station) },
                            onLongClick = if (reordering) null else ({ editingStation = station }),
                            modifier = Modifier.semantics {
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction(moveUp) {
                                        viewModel.moveSavedStation(index, index - 1)
                                        true
                                    }.takeIf { index > 0 },
                                    CustomAccessibilityAction(moveDown) {
                                        viewModel.moveSavedStation(index, index + 1)
                                        true
                                    }.takeIf { index < savedOrder.lastIndex }
                                )
                            },
                            trailing = {
                                if (reordering) {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(
                                            onDragStopped = {
                                                viewModel.reorderSavedStations(savedOrder.map { it.id })
                                            }
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = stringResource(R.string.drag_to_reorder)
                                        )
                                    }
                                } else {
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
                            }
                        )
                    }
                }
            }
        } else {
            item(key = "import-row") {
                ImportExportRow(
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = {},
                    onExportBackup = {},
                    exportEnabled = false
                )
            }
        }

        if (uiState.recentStations.isNotEmpty()) {
            item(key = "recent-header") {
                HorizontalDivider()
                SectionHeader(stringResource(R.string.recent_stations))
            }
            items(
                uiState.recentStations.take(StationCollections.RECENT_LIBRARY_LIMIT),
                key = { "recent-${it.id}" }
            ) { station ->
                StationRow(
                    station = station,
                    isPlaying = station.id == uiState.selectedStationId,
                    onPlay = { viewModel.playStation(station) },
                    trailing = {
                        IconButton(onClick = { viewModel.removeRecentStation(station.id) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove_from_recents)
                            )
                        }
                    }
                )
            }
            item(key = "recent-clear") {
                TextButton(
                    onClick = { viewModel.clearRecentStations() },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.clear_recent_stations))
                }
            }
        }

        if (uiState.heardTracks.isNotEmpty()) {
            item(key = "top-header") {
                HorizontalDivider()
                SectionHeader(stringResource(R.string.top_tracks))
                TopTracksTimeframePicker(
                    selected = topTracksTimeframe,
                    onSelect = { topTracksTimeframe = it }
                )
            }
            if (topTracks.isEmpty()) {
                item(key = "top-empty") {
                    Text(
                        text = stringResource(R.string.top_tracks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(topTracks, key = { "top-${it.title.lowercase()}\u001F${it.artist.lowercase()}" }) { track ->
                    ListItem(
                        leadingContent = { TrackArtwork(track.artworkUrl) },
                        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Text(
                                text = stringResource(R.string.top_track_play_count, track.playCount),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        item(key = "recently-heard") {
            HorizontalDivider()
            RecentlyHeardRow(count = uiState.heardTracks.size, onClick = onOpenRecentlyHeard)
        }

        // Escape hatch back to the app's own stream
        if (uiState.selectedStationId != null) {
            item(key = "default-stream") {
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

private fun savedKey(station: Station): String = "saved-${station.id}"

@Composable
private fun TopTracksTimeframePicker(
    selected: TopTracksTimeframe,
    onSelect: (TopTracksTimeframe) -> Unit
) {
    val options = TopTracksTimeframe.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        options.forEachIndexed { index, timeframe ->
            SegmentedButton(
                selected = timeframe == selected,
                onClick = { onSelect(timeframe) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(
                    stringResource(
                        when (timeframe) {
                            TopTracksTimeframe.WEEK -> R.string.top_tracks_week
                            TopTracksTimeframe.MONTH -> R.string.top_tracks_month
                            TopTracksTimeframe.ALL_TIME -> R.string.top_tracks_all_time
                        }
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RecentlyHeardRow(count: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.recently_heard)) },
        trailingContent = {
            if (count > 0) {
                Badge { Text(count.toString()) }
            }
        }
    )
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ImportExportRow(
    onImport: () -> Unit,
    onExport: () -> Unit,
    onExportBackup: () -> Unit,
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
        IconButton(onClick = onExportBackup, enabled = exportEnabled) {
            Icon(Icons.Default.Backup, contentDescription = stringResource(R.string.export_favorites_backup))
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
 * The picked document's display name, used to tell PLS from M3U (and as a fallback signal
 * for a JSON backup). Content pickers rarely report a trustworthy MIME type for playlist
 * files, so the file extension is the more reliable signal.
 */
private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment
