package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station

/**
 * "Favorite this station" — ShoutKit's Siri intent of the same name — acting on whatever is
 * currently selected, with no UI open: the static app shortcut, the Assistant capability
 * and the heart in the media notification all come through here.
 *
 * Only a station with an id can be saved. The app's own stream is never a [Station] (it is
 * what plays when the selection is `null`), so it is reported as [Outcome.DefaultStream]
 * rather than as an error; a selection with no id or no stream URL (an unusable, partly
 * written entry) is [Outcome.NothingSelected]. Kept free of DataStore so every case is a
 * plain JVM test; [SettingsRepository.favoriteSelectedStation] applies it in one transaction.
 */
object FavoriteCurrentStation {

    sealed interface Outcome {
        /** The selection has no identity to save under. */
        data object NothingSelected : Outcome

        /** The app's own stream is playing; it is always offered and cannot be saved. */
        data object DefaultStream : Outcome

        /** [station] was appended to My Stations. */
        data class Added(val station: Station) : Outcome

        /** [station] was already saved, and the request was add-only. */
        data class AlreadySaved(val station: Station) : Outcome

        /** [station] was saved and the request was a toggle, so it was removed. */
        data class Removed(val station: Station) : Outcome
    }

    /** Whether [selected] can be saved at all (see [Outcome.NothingSelected]). */
    fun isFavoritable(selected: Station?): Boolean =
        selected != null && selected.id.isNotBlank() && selected.isPlayable

    /** Whether the current selection is in [saved] — what the notification heart shows. */
    fun isSaved(selected: Station?, saved: List<Station>): Boolean =
        isFavoritable(selected) && saved.any { it.id == selected!!.id }

    /**
     * What favouriting [selected] does to [saved]. Add-only by default (the shortcut: saying
     * it twice must not unsave); [toggle] removes an already saved station (the heart).
     */
    fun decide(selected: Station?, saved: List<Station>, toggle: Boolean = false): Outcome = when {
        selected == null -> Outcome.DefaultStream
        !isFavoritable(selected) -> Outcome.NothingSelected
        saved.none { it.id == selected.id } -> Outcome.Added(selected)
        toggle -> Outcome.Removed(selected)
        else -> Outcome.AlreadySaved(selected)
    }

    /** [saved] after [outcome]; unchanged for every outcome that is not a write. */
    fun apply(saved: List<Station>, outcome: Outcome): List<Station> = when (outcome) {
        is Outcome.Added -> StationCollections.addFavorite(saved, outcome.station)
        is Outcome.Removed -> StationCollections.removeFavorite(saved, outcome.station.id)
        else -> saved
    }
}
