package com.cascadiacollections.sir.ui

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.TransportAction
import com.cascadiacollections.sir.core.artwork.StationMonogram
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.StreamFailure

/**
 * Album art when the iTunes lookup found some, otherwise the station's own artwork,
 * crossfading between the two as tracks change.
 *
 * Decorative for accessibility: the station and track are announced by the text beside it.
 */
@Composable
internal fun NowPlayingArtwork(
    albumArtUrl: String?,
    station: Station?,
    stationName: String,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size).clip(shape)) {
        Crossfade(targetState = albumArtUrl, label = "nowPlayingArtwork") { url ->
            var albumFailed by remember(url) { mutableStateOf(false) }
            if (url == null || albumFailed) {
                StationArtworkImage(station = station, stationName = stationName, size = size)
            } else {
                AsyncImage(
                    model = sizedRequest(url, size),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { albumFailed = true },
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

/**
 * The station's favicon, decoded at the size it is shown rather than at whatever size the
 * station uploaded; when it is missing or fails to load, a monogram tile in the station's
 * own colour.
 */
@Composable
internal fun StationArtworkImage(
    station: Station?,
    stationName: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val favicon = station?.favicon?.takeIf { it.isNotBlank() }
    var faviconFailed by remember(station?.id, favicon) { mutableStateOf(false) }
    if (favicon == null || faviconFailed) {
        StationMonogramTile(
            monogram = StationMonogram.of(station?.id, station?.name?.takeIf { it.isNotBlank() } ?: stationName),
            size = size,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = sizedRequest(favicon, size),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { faviconFailed = true },
            modifier = modifier.size(size),
        )
    }
}

/** A generated placeholder: the station's initials on a colour derived from its identity. */
@Composable
internal fun StationMonogramTile(
    monogram: StationMonogram,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .size(size)
            .background(Color.hsl(monogram.hue, MONOGRAM_SATURATION, MONOGRAM_LIGHTNESS)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram.initials,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (maxWidth.value * MONOGRAM_TEXT_SCALE).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun sizedRequest(url: String, size: Dp): ImageRequest {
    val px = with(LocalDensity.current) { size.roundToPx() }
    return ImageRequest.Builder(LocalPlatformContext.current)
        .data(url)
        .size(px)
        .build()
}

// Mid lightness keeps white initials readable on every hue.
private const val MONOGRAM_SATURATION = 0.45f
private const val MONOGRAM_LIGHTNESS = 0.40f
private const val MONOGRAM_TEXT_SCALE = 0.36f

/** ShoutKit's copy for a failure: the long form on Listen, the short one on the mini-player. */
@StringRes
internal fun StreamFailure.displayMessageRes(short: Boolean): Int = when (this) {
    StreamFailure.NoNetwork ->
        if (short) R.string.failure_no_network_short else R.string.failure_no_network
    is StreamFailure.StationUnavailable ->
        if (short) R.string.failure_station_unavailable_short else R.string.failure_station_unavailable
    StreamFailure.Unplayable, StreamFailure.Transient ->
        if (short) R.string.failure_stream_error_short else R.string.failure_stream_error
    StreamFailure.Stalled ->
        if (short) R.string.failure_stalled_short else R.string.failure_stalled
}

internal val TransportAction.icon: ImageVector
    get() = when (this) {
        TransportAction.PLAY -> Icons.Default.PlayArrow
        TransportAction.PAUSE -> Icons.Default.Pause
        TransportAction.CANCEL -> Icons.Default.Close
        TransportAction.RETRY -> Icons.Default.Refresh
    }

@get:StringRes
internal val TransportAction.labelRes: Int
    get() = when (this) {
        TransportAction.PLAY -> R.string.play
        TransportAction.PAUSE -> R.string.pause
        TransportAction.CANCEL -> R.string.cancel_connection
        TransportAction.RETRY -> R.string.retry
    }
