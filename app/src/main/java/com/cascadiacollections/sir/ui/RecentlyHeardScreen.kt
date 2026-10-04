package com.cascadiacollections.sir.ui

import android.content.ClipData
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.core.persistence.HeardTrack
import kotlinx.coroutines.launch

/**
 * The full persisted track history, opened from the Library's "Recently Heard" row —
 * ShoutKit's `RecentlyHeardListView`. Top Tracks is the summary; this is the drill-in.
 *
 * Tapping a row copies "Title — Artist", as the player's track history sheet does.
 *
 * @param nowMillis the reference time for the relative timestamps; injectable for tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyHeardScreen(
    tracks: List<HeardTrack>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis()
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recently_heard)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (tracks.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (tracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.recently_heard_empty),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.recently_heard_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // No item keys: two hearings can legitimately share title, artist and time.
                items(tracks) { track ->
                    val relative = DateUtils.getRelativeTimeSpanString(
                        track.timestampMillis,
                        nowMillis,
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                    ListItem(
                        modifier = Modifier.clickable(
                            onClickLabel = stringResource(R.string.copy_track)
                        ) {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText(track.title, track.copyText))
                                )
                            }
                        },
                        leadingContent = { TrackArtwork(track.artworkUrl) },
                        headlineContent = {
                            Text(track.title.ifBlank { stringResource(R.string.unknown_track) })
                        },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    track.artist,
                                    track.stationName.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                            )
                        },
                        trailingContent = {
                            Text(
                                text = relative,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}
