package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import java.util.Locale

/**
 * Rules for the home-screen Quick Play widget, a port of ShoutKit's Quick Play.
 *
 * A widget plays one favourite: the one the user pinned in its configuration screen, or —
 * until they pin one, or once the pinned station is unsaved — the first saved station.
 * Its button therefore means "play *this* station": when that station is not the current
 * selection the tap selects it and starts it; only when it already is the current selection
 * does the tap toggle play/pause. Kept here, free of Glance and Media3, so the decision is
 * covered by plain JVM tests.
 */
object QuickPlaySelection {

    /** What a tap on the widget's play button should do. */
    sealed interface TapAction {
        /** Select [station] (recording it as a play) and start it. */
        data class SelectAndPlay(val station: Station) : TapAction
        data object Play : TapAction
        data object Pause : TapAction
    }

    /**
     * The station a widget plays: [pinnedId] if it is still saved, else the first saved
     * station, else `null` (no favourites — the widget falls back to the current stream).
     */
    fun resolveStation(saved: List<Station>, pinnedId: String?): Station? =
        pinnedId?.let { id -> saved.firstOrNull { it.id == id } } ?: saved.firstOrNull()

    /** Whether the widget's station is the one currently selected for playback. */
    fun isCurrent(widgetStation: Station?, selected: Station?): Boolean =
        widgetStation == null || widgetStation.id == selected?.id

    /**
     * The widget shows a pause button only while *its* station is playing; while another
     * station plays, the button offers to switch to this one.
     */
    fun showsPause(widgetStation: Station?, selected: Station?, isPlaying: Boolean): Boolean =
        isPlaying && isCurrent(widgetStation, selected)

    fun tapAction(widgetStation: Station?, selected: Station?, isPlaying: Boolean): TapAction = when {
        widgetStation != null && !isCurrent(widgetStation, selected) -> TapAction.SelectAndPlay(widgetStation)
        isPlaying -> TapAction.Pause
        else -> TapAction.Play
    }

    /**
     * Up to two initials for the artwork placeholder: the first letter of the first two
     * words ("Radio Paradise" → "RP"), or the first letter of a one-word name.
     */
    fun initials(name: String): String =
        name.split(Regex("[\\s_\\-]+"))
            .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
            .take(2)
            .joinToString("")
            .uppercase(Locale.ROOT)
}
