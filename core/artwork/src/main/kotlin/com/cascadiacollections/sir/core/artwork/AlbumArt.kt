package com.cascadiacollections.sir.core.artwork

/**
 * Cover art found for the track that is currently playing.
 *
 * [artworkUrl] is already upscaled to [ITunesSearch.ARTWORK_SIZE]; [trackViewUrl] is the
 * Apple Music page for the song, when the search returned one.
 */
data class AlbumArt(val artworkUrl: String, val trackViewUrl: String? = null)

/**
 * The identity of a track for lookup and caching purposes.
 *
 * Case and surrounding whitespace are not part of a track's identity: stations re-announce
 * the same song with different capitalisation, and each variant must hit the same cache
 * entry rather than spend another request.
 */
data class TrackKey private constructor(val artist: String, val title: String) {
    companion object {
        /** A key for the pair, or null when either half is blank and there is nothing to look up. */
        fun of(artist: String?, title: String?): TrackKey? {
            val a = artist?.trim()?.lowercase().orEmpty()
            val t = title?.trim()?.lowercase().orEmpty()
            if (a.isEmpty() || t.isEmpty()) return null
            return TrackKey(a, t)
        }
    }
}
