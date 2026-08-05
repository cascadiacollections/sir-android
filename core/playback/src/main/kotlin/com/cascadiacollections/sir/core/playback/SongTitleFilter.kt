package com.cascadiacollections.sir.core.playback

/**
 * A conservative gate for whether parsed track info looks like an actual song, applied
 * before it reaches the notification or any now-playing surface.
 *
 * Broadcasters' real song titles are far too varied to enumerate, so this defaults to
 * keeping everything and rejects only on a *positive* signal of junk: a bare URL, the
 * station's own name, promotional copy, or a single unadorned ID token. A rejected update
 * is dropped rather than blanked, so the previous good track stays on screen.
 *
 * Ported from ShoutKit's `SongTitleFilter`. Complements [StreamMetadataResolver]'s
 * `staticTitles`, which handles the narrower case of a station that always sends one known
 * constant: this catches the junk we cannot enumerate in advance.
 */
object SongTitleFilter {

    /**
     * Substrings seen in broadcaster promo copy that leaks into `StreamTitle` between
     * songs. Lowercased, checked as substring containment.
     */
    private val promoPhrases = setOf(
        "now playing", "listen live", "on air", "follow us", "like us",
        "text the word", "text to win", "call now", "visit us", "check us out",
        "download our app", "commercial break", "stay tuned", "coming up next",
        "back after this", "brought to you by",
    )

    /**
     * Bare single-word placeholders broadcasters send when no track is known, as distinct
     * from a legitimately one-word song title.
     */
    private val junkSingleWords = setOf(
        "unknown", "stream", "live", "offline", "test", "advertisement",
    )

    private val knownTlds = listOf(".com", ".org", ".net", ".fm", ".io", ".co")

    /**
     * @param stationName the station's display name, used to reject a title that is just
     *   the station plugging itself. Null skips that check rather than failing it.
     */
    fun isLikelySongTitle(track: IcyTrack, stationName: String?): Boolean {
        val title = track.title?.trim()?.takeUnless(String::isEmpty) ?: return false
        val candidates = listOfNotNull(title, track.artist)

        if (candidates.any(::looksLikeUrl)) return false
        if (stationName != null && candidates.any { matchesStationName(it, stationName) }) return false
        if (candidates.any(::containsPromoPhrasing)) return false
        if (track.artist == null && isBareSingleWordId(title)) return false
        return true
    }

    private fun looksLikeUrl(text: String): Boolean {
        val lowercased = text.lowercase()
        if (lowercased.contains("http://") ||
            lowercased.contains("https://") ||
            lowercased.contains("www.")
        ) {
            return true
        }

        // A bare domain, e.g. "kexp.org", with no other words around it.
        if (text.any(Char::isWhitespace)) return false
        return knownTlds.any { lowercased.endsWith(it) || lowercased.contains("$it/") }
    }

    private fun matchesStationName(text: String, stationName: String): Boolean {
        val normalizedStation = normalizeForComparison(stationName)
        if (normalizedStation.isEmpty()) return false
        return normalizeForComparison(text) == normalizedStation
    }

    /**
     * Strips everything but letters and digits, so punctuation and spacing differences
     * between a `StreamTitle` station plug and the canonical station name (e.g. "KEXP 90.3
     * FM" vs "KEXP903FM") don't defeat the match.
     */
    private fun normalizeForComparison(text: String): String =
        text.lowercase().filter(Char::isLetterOrDigit)

    private fun containsPromoPhrasing(text: String): Boolean {
        val lowercased = text.lowercase()
        return promoPhrases.any { lowercased.contains(it) }
    }

    /**
     * A single token with no artist is either a real one-word song title or a placeholder
     * ID — only the latter carries a digit or is a known filler word.
     */
    private fun isBareSingleWordId(title: String): Boolean {
        if (title.any(Char::isWhitespace)) return false
        if (title.lowercase() in junkSingleWords) return true
        return title.any(Char::isDigit)
    }
}
