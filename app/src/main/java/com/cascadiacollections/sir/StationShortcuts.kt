package com.cascadiacollections.sir

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.cascadiacollections.sir.core.model.Station

/**
 * Publishes per-station dynamic home-screen shortcuts, alongside the static "Play",
 * "What's playing?" and "Favorite this station" shortcuts declared in `shortcuts.xml`.
 *
 * Each shortcut targets the same `sir://station/{id}` deep link `MainActivity` already
 * resolves for other entry points (widgets, Assistant), so there's no separate
 * playback path to maintain here — long-pressing the app icon and tapping the shortcut
 * behaves exactly like following that link.
 */
object StationShortcuts {

    private const val ID_PREFIX = "station-"

    // Pinned play shortcuts (requestPin); never shared with the dynamic ones above.
    private const val PIN_PREFIX = "play-"

    /**
     * The `<shortcut>`s in `res/xml/shortcuts.xml`. The launcher's per-activity limit
     * counts static and dynamic shortcuts together, so these come off the stations' share:
     * the static ones are the app's fixed actions and always win the slots.
     */
    const val STATIC_SHORTCUT_COUNT = 3

    /** How many station shortcuts fit beside the static ones under [maxPerActivity]. */
    fun dynamicCapacity(maxPerActivity: Int): Int = (maxPerActivity - STATIC_SHORTCUT_COUNT).coerceAtLeast(0)

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
        val maxCount =
            dynamicCapacity(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))

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
                // Station IDs derived from imported stream URLs can contain reserved
                // characters like ':' and '/'; the link percent-encodes them so
                // MainActivity's lastPathSegment gets the whole ID back intact.
                Intent(Intent.ACTION_VIEW, StationDeepLink.stationLink(station.id))
                    .setClass(context, MainActivity::class.java)
            )
            .build()

    /** Whether the launcher can pin a shortcut on request ([requestPin]). */
    fun canPin(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Asks the launcher to pin a home-screen shortcut that plays [station] without opening
     * the app (`sir://play/{id}` through [PlayStationActivity]) — the one-tap version of an
     * automation link, and something routines that can only "open a shortcut" can use.
     *
     * Its own id prefix: a pinned shortcut sharing a dynamic shortcut's id would be rewritten
     * by the next [update], which points at MainActivity. Returns false when the launcher
     * can't pin; otherwise the launcher shows its own confirmation.
     */
    fun requestPin(context: Context, station: Station): Boolean {
        if (!canPin(context) || !station.isPlayable) return false
        val shortcut = ShortcutInfoCompat.Builder(context, PIN_PREFIX + station.id)
            .setShortLabel(station.name.ifBlank { context.getString(R.string.play_station_link_label) })
            .setLongLabel(station.name.ifBlank { context.getString(R.string.play_station_link_label) })
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground))
            .setIntent(
                Intent(PlayStationActivity.ACTION_PLAY_STATION, StationDeepLink.playLink(station.id))
                    .setClass(context, PlayStationActivity::class.java)
            )
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
