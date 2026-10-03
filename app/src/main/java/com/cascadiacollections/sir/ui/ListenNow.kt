package com.cascadiacollections.sir.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.core.model.Station

object ListenNowTestTags {
    const val RECENT_SHELF = "listen_now_recent_shelf"
    const val SAVED_STATIONS_NOTICE = "listen_now_saved_notice"
    fun recentTile(id: String) = "listen_now_recent_$id"
    fun popularTile(id: String) = "listen_now_popular_$id"
}

/** One entry of a tile's long-press menu, mirrored as a custom accessibility action. */
internal data class TileAction(val label: String, val onClick: () -> Unit)

/**
 * ShoutKit's Recently Played shelf: a horizontal row of station tiles. Tapping plays;
 * the long-press menu (or the matching accessibility action) hides a tile.
 */
@Composable
internal fun RecentlyPlayedShelf(
    stations: List<Station>,
    selectedStationId: String?,
    onPlay: (Station) -> Unit,
    onHide: (Station) -> Unit,
    modifier: Modifier = Modifier
) {
    val hideLabel = stringResource(R.string.hide_from_recently_played)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .testTag(ListenNowTestTags.RECENT_SHELF),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stations.forEach { station ->
            StationTile(
                station = station,
                isPlaying = station.id == selectedStationId,
                onPlay = { onPlay(station) },
                actions = listOf(TileAction(hideLabel) { onHide(station) }),
                testTag = ListenNowTestTags.recentTile(station.id),
                modifier = Modifier.width(RECENT_TILE_SIZE)
            )
        }
    }
}

/** A Popular Stations grid tile; the long-press menu saves or unsaves the station. */
@Composable
internal fun PopularStationTile(
    station: Station,
    isSaved: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(if (isSaved) R.string.remove_from_my_stations else R.string.add_to_my_stations)
    StationTile(
        station = station,
        isPlaying = isPlaying,
        onPlay = onPlay,
        actions = listOf(TileAction(label, onToggleSaved)),
        testTag = ListenNowTestTags.popularTile(station.id),
        modifier = modifier
    )
}

/**
 * Square artwork (or the station's monogram) with the name under it and a play badge.
 *
 * One merged semantics node: TalkBack reads the station name, the tap is "play", and the
 * long-press menu's entries are offered again as custom actions, since a long-press menu
 * is not discoverable from a screen reader.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationTile(
    station: Station,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    actions: List<TileAction>,
    testTag: String,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val playLabel = stringResource(R.string.play_station)
    val shape = RoundedCornerShape(12.dp)

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    enabled = station.isPlayable,
                    onClickLabel = playLabel,
                    onClick = onPlay,
                    onLongClick = { menuOpen = true }
                )
                .testTag(testTag)
                .semantics(mergeDescendants = true) {
                    customActions = actions.map { action ->
                        CustomAccessibilityAction(action.label) {
                            action.onClick()
                            true
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                StationArtworkImage(
                    station = station,
                    stationName = station.name,
                    size = maxWidth,
                    modifier = Modifier.clip(shape)
                )
                Surface(
                    shape = CircleShape,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isPlaying) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(PLAY_BADGE_SIZE)
                ) {
                    // Decorative: the tile's click label already says "Play station".
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        menuOpen = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

/** Shown when a refresh failed but older stations are still on screen. */
@Composable
internal fun SavedStationsNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(ListenNowTestTags.SAVED_STATIONS_NOTICE),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(R.string.showing_saved_stations),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val RECENT_TILE_SIZE = 104.dp
private val PLAY_BADGE_SIZE = 28.dp
internal val POPULAR_TILE_MIN_WIDTH = 160.dp
