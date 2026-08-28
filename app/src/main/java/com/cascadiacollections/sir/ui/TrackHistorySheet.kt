package com.cascadiacollections.sir.ui

import android.content.ClipData
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.core.playback.TrackHistoryEntry
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Bottom sheet listing the tracks resolved from ICY metadata during this session,
 * newest first, each copyable to the clipboard as "Title — Artist".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackHistorySheet(
    history: List<TrackHistoryEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.track_history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.track_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(history, key = { "${it.timestampMillis}-${it.title}-${it.artist}" }) { entry ->
                        val timeLabel = remember(entry.timestampMillis) {
                            DateFormat.getTimeFormat(context).format(Date(entry.timestampMillis))
                        }
                        ListItem(
                            headlineContent = { Text(entry.title) },
                            supportingContent = {
                                Text(listOfNotNull(entry.artist, timeLabel).joinToString(" • "))
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        val text = listOfNotNull(entry.title, entry.artist).joinToString(" — ")
                                        scope.launch {
                                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(entry.title, text)))
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_track)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
