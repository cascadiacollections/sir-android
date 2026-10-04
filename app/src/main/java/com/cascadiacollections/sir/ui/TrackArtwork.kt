package com.cascadiacollections.sir.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Cover art for a heard or top track (ShoutKit shows it on both lists), falling back to a
 * music note when the lookup found none or the image fails to load. Decorative: the row's
 * text already names the track.
 */
@Composable
internal fun TrackArtwork(artworkUrl: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    var failed by remember(artworkUrl) { mutableStateOf(false) }
    if (artworkUrl.isNullOrBlank() || failed) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(size)
        )
    } else {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = modifier.size(size).clip(RoundedCornerShape(4.dp))
        )
    }
}
