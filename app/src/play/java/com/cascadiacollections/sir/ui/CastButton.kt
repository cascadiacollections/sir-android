package com.cascadiacollections.sir.ui

import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector

private const val TAG = "CastButton"

/**
 * The system Cast icon, opening the standard device-picker dialog when tapped.
 *
 * Uses the same [MediaControlIntent.CATEGORY_REMOTE_PLAYBACK] selector
 * [com.cascadiacollections.sir.CastDeviceDetector] already scans with, and needs
 * nothing from `play-services-cast`/the `:cast` module to do so: the Cast SDK
 * registers its own [androidx.mediarouter.media.MediaRouter] route provider from its
 * manifest metadata alone, so any [MediaRouteButton] bound to this selector discovers
 * the same Cast devices and, once the user picks one, the framework establishes a real
 * Cast session that `:cast`'s `CastSessionCoordinator` reacts to independently.
 *
 * [MediaRouteButton] resolves several of its styling attributes (`mediaRouteButtonStyle`
 * and friends) against an AppCompat-flavored theme; `Theme.Sir` is a plain
 * `android:Theme.Material` rather than `Theme.AppCompat`, and this hasn't been verified
 * against it on a real device. Constructing/styling it is wrapped defensively so a theme
 * mismatch degrades to an absent button rather than crashing the whole screen — the same
 * defensive posture [com.cascadiacollections.sir.CastDeviceDetector] and `SirCastPlayer`
 * already take around other Cast APIs that can't be relied on unconditionally.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            runCatching {
                MediaRouteButton(context).apply {
                    routeSelector = MediaRouteSelector.Builder()
                        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                        .build()
                }
            }.getOrElse { e ->
                Log.w(TAG, "MediaRouteButton unavailable in this theme", e)
                View(context).apply { visibility = View.GONE }
            }
        }
    )
}
