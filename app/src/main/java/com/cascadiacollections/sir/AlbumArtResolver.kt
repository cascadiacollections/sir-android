package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.artwork.AlbumArt
import com.cascadiacollections.sir.core.artwork.AlbumArtLookup
import com.cascadiacollections.sir.core.artwork.TrackKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Keeps [current] in step with the track that is playing, for [RadioPlaybackService].
 *
 * Owned by the service rather than a ViewModel so the notification, lock screen, Auto and
 * Wear get the artwork whether or not the UI is alive, and so one lookup serves all of them.
 * When [enabled] is false no lookup is made at all, and any art already shown is dropped.
 *
 * [onChanged] runs whenever [current] changes after the fact (a lookup finishing, the
 * setting flipping); a change caused by [onTrackChanged] is reported by its return value
 * instead, so the caller can fold it into the metadata update it is already making.
 */
internal class AlbumArtResolver(
    private val scope: CoroutineScope,
    private val enabled: Flow<Boolean>,
    private val lookup: suspend (artist: String, title: String) -> AlbumArt?,
    private val onChanged: () -> Unit,
) {
    /** Artwork for the current track, or null when there is none (yet). */
    var current: AlbumArt? = null
        private set

    private var isEnabled = false
    private var track: Pair<String, String>? = null
    private var job: Job? = null

    fun start() {
        scope.launch {
            enabled.distinctUntilChanged().collect { value ->
                isEnabled = value
                if (value) {
                    resolve()
                } else {
                    job?.cancel()
                    if (current != null) {
                        current = null
                        onChanged()
                    }
                }
            }
        }
    }

    /**
     * Records the newly playing track and starts a lookup for it. Returns true when this
     * cleared artwork belonging to the previous track.
     */
    fun onTrackChanged(artist: String?, title: String?): Boolean {
        val next = TrackKey.of(artist, title)?.let { artist.orEmpty().trim() to title.orEmpty().trim() }
        if (next == track) return false
        track = next
        job?.cancel()
        val cleared = current != null
        current = null
        resolve()
        return cleared
    }

    private fun resolve() {
        val wanted = track ?: return
        if (!isEnabled) return
        job?.cancel()
        job = scope.launch {
            val art = lookup(wanted.first, wanted.second)
            if (art != null && track == wanted && isEnabled && art != current) {
                current = art
                onChanged()
            }
        }
    }
}

/** The process-wide lookup, so its cache outlives any one service instance. */
internal object AppAlbumArt {
    private val lookup by lazy { AlbumArtLookup() }

    suspend fun lookup(artist: String, title: String): AlbumArt? =
        lookup.lookup(artist, title, Locale.getDefault().country)
}
