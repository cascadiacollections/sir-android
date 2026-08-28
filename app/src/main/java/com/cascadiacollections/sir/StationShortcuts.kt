package com.cascadiacollections.sir

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.cascadiacollections.sir.core.model.Station

/**
 * Publishes per-station dynamic home-screen shortcuts, alongside the static "Play"
 * shortcut declared in `shortcuts.xml`.
 *
 * Each shortcut targets the same `sir://station/{id}` deep link `MainActivity` already
 * resolves for other entry points (widgets, Assistant), so there's no separate
 * playback path to maintain here — long-pressing the app icon and tapping the shortcut
 * behaves exactly like following that link.
 */
object StationShortcuts {

    private const val ID_PREFIX = "station-"

    /**
     * Replaces the app's dynamic shortcuts with one per station in [stations] (most
     * important first), capped at whatever the launcher actually supports. Called with
     * an empty or shrunk list, this correctly clears shortcuts for stations no longer
     * saved — `setDynamicShortcuts` replaces the whole set rather than only adding.
     */
    fun update(context: Context, stations: List<Station>) {
        // maxCount <= 0 means this launcher doesn't support shortcuts at all (rather
        // than "zero slots free") — still clear any shortcuts a previous launcher may
        // have left behind, rather than returning early and leaving them stale.
        val maxCount = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(0)

        val shortcuts = stations
            .filter { it.isPlayable && it.name.isNotBlank() }
            .take(maxCount)
            .mapIndexed { index, station -> shortcutFor(context, station, rank = index) }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun shortcutFor(context: Context, station: Station, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, ID_PREFIX + station.id)
            .setShortLabel(station.name)
            .setLongLabel(station.name)
            // Shortcuts sort ascending by rank, and stations arrive most-played-first,
            // so rank == position preserves that ordering — without it every shortcut
            // defaults to rank 0 and the launcher is free to order them arbitrarily.
            .setRank(rank)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground))
            .setIntent(
                Intent(Intent.ACTION_VIEW, deepLinkFor(station.id))
                    .setClass(context, MainActivity::class.java)
            )
            .build()

    // Station IDs derived from imported stream URLs can contain reserved characters
    // like ':' and '/'; appendPath percent-encodes them so MainActivity's
    // lastPathSegment gets the whole ID back intact instead of just its trailing chunk.
    private fun deepLinkFor(stationId: String): Uri =
        Uri.Builder().scheme("sir").authority("station").appendPath(stationId).build()
}
