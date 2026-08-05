package com.cascadiacollections.sir.core.playback

/**
 * Track info parsed out of a stream's metadata. Both halves are independently optional:
 * a dialect may carry only a title, and a suppressed block carries neither.
 */
data class IcyTrack(val title: String? = null, val artist: String? = null) {
    companion object {
        /** Nothing usable in the block — the caller keeps whatever it already had. */
        val NONE = IcyTrack()
    }
}

/**
 * Parses ICY (SHOUTcast/Icecast) and broadcaster-HLS stream metadata into structured
 * track info.
 *
 * Media3 hands us the raw `StreamTitle` and nothing else: `IcyInfo.populateMediaMetadata`
 * sets `MediaMetadata.title` and never `artist`, so without this the notification's title
 * carries the whole `"Artist - Song"` string and the artist line is never populated at all.
 * Splitting it is deliberately our job — Media3 surfaces the wire value untouched.
 *
 * There is no single spec broadcasters agree on. In the wild:
 * - Classic ICY: `key='value';` pairs, canonically `StreamTitle='Artist - Song';StreamUrl='…';`,
 *   with artist and title bundled into one field.
 * - Comma-separated, double-quoted broadcaster HLS (Z100/iHeartRadio-style):
 *   `title="Boom Boom Pow",artist=Black Eyed Peas`, already split.
 * - Triton Digital cue metadata: comma- *or* space-separated `key="value"` pairs, where
 *   `text` bundles "Artist - Title" — or, between songs, an ad-break marker with no song
 *   in it at all.
 * - **Nested** dialects: iHeart wraps a whole cue block inside the classic ICY field, with
 *   a leading empty-artist separator: `StreamTitle=' - text="Spot Block End" amgTrackId="…"'`.
 *   So every extraction step re-checks its result for another layer of wire format.
 *
 * Rather than whitelisting each dialect's key set, [fields] detects wire format
 * *structurally* — any string that fully tokenizes into two or more `key=value` pairs — so
 * an unfamiliar broadcaster's field names don't leak onto the screen before we know what
 * they mean. Only title-bearing keys are ever surfaced; anything that still looks like
 * `key="value"` soup after unwrapping is suppressed rather than displayed.
 *
 * Ported from ShoutKit's `ICYMetadataParser`, which learned every one of these cases from
 * a live stream. Pure string logic, so it is covered by plain JVM tests.
 */
object IcyMetadataParser {

    /**
     * Keys recognized even as the *only* field in a block. A lone `StreamTitle='…';` is
     * common; a lone `TrackId=123` is not something worth guessing about, so it is absent.
     */
    private val recognizedSingleFieldKeys = setOf(
        "streamtitle", "streamurl", "title", "artist", "album", "text",
    )

    /** Keys whose value bundles "Artist - Title" together, in priority order. */
    private val combinedTitleKeys = listOf("streamtitle", "text")

    /**
     * Triton Digital ad-break cue markers delivered in the `text` field. They describe the
     * break, not what is playing, and reading one as a song title looks like a glitch.
     */
    private val adCueMarkers = setOf("spot block start", "spot block end")

    /**
     * A bare `=` stays allowed — legitimate titles like "E=MC²" contain one — but a quote
     * immediately after it means we are looking at wire format we failed to decompose.
     */
    private val soupMarkers = listOf("=\"", "='")

    /** Observed dialects nest one level; the rest is a backstop against a pathological block. */
    private const val MAX_NESTING_DEPTH = 4

    private const val ARTIST_TITLE_SEPARATOR = " - "

    fun parseTrack(rawMetadata: String): IcyTrack = parse(rawMetadata, depth = 0)

    private fun parse(rawMetadata: String, depth: Int): IcyTrack {
        if (depth >= MAX_NESTING_DEPTH) return IcyTrack.NONE

        val fields = fields(rawMetadata)
        if (fields != null) {
            // A combined field's value may itself be another layer of wire format.
            for (key in combinedTitleKeys) {
                val combined = fields[key]
                if (combined != null) return parse(combined, depth + 1)
            }

            if (fields.containsKey("title") || fields.containsKey("artist")) {
                return IcyTrack(
                    title = fields["title"]?.trim()?.takeUnless(String::isEmpty),
                    artist = fields["artist"]?.trim()?.takeUnless(String::isEmpty),
                )
            }

            // Recognized wire format with no title-bearing key (cue metadata carrying only
            // TrackId/length, say): suppress rather than display a key dump.
            return IcyTrack.NONE
        }

        val info = splitArtistTitle(rawMetadata)
        val title = info.title ?: return info

        // A leading " - " hides a nested block from the tokenizer; the split strips it, so
        // the title half has to be re-checked for wire format.
        if (fields(title) != null) return parse(title, depth + 1)

        if (info.artist == null && title.lowercase() in adCueMarkers) return IcyTrack.NONE

        // Checked in both halves: the " - " split can land the soup on the artist side.
        if (soupMarkers.any { title.contains(it) }) return IcyTrack.NONE
        val artist = info.artist
        if (artist != null && soupMarkers.any { artist.contains(it) }) return IcyTrack.NONE

        return info
    }

    /**
     * Tokenizes a wire-format block into lowercased-key fields, or returns null when the
     * string doesn't fully decompose into `key=value` pairs — or decomposes into a single
     * pair whose key isn't independently recognizable, which keeps a legitimate title like
     * `E=MC² - Song` out of the tokenizer.
     *
     * No dialect defines escaping, so a quoted value ends at the first occurrence of its
     * quote character that is itself followed by another field boundary (see
     * [looksLikeFieldBoundary]). That tolerates apostrophes in titles like
     * `StreamTitle='Don't Stop';` without being fooled by them.
     */
    internal fun fields(rawMetadata: String): Map<String, String>? {
        val fields = LinkedHashMap<String, String>()
        var cursor = 0
        val end = rawMetadata.length

        while (true) {
            while (cursor < end && isSeparator(rawMetadata[cursor])) cursor++
            if (cursor >= end) break

            val equals = rawMetadata.indexOf('=', cursor)
            if (equals < 0) return null
            val key = rawMetadata.substring(cursor, equals).trim().lowercase()
            if (key.isEmpty() || !key.all(::isKeyCharacter)) return null
            cursor = equals + 1

            val quote = rawMetadata.getOrNull(cursor)
            val value: String
            if (quote == '\'' || quote == '"') {
                cursor++
                val close = closingQuote(rawMetadata, from = cursor, quote = quote)
                if (close != null) {
                    value = rawMetadata.substring(cursor, close)
                    cursor = close + 1
                } else {
                    value = rawMetadata.substring(cursor)
                    cursor = end
                }
            } else {
                // An unquoted value may contain separator characters of its own
                // (`StreamTitle=Earth, Wind & Fire - September;`, `artist=Tyler, The
                // Creator`), so it ends only at a separator actually followed by another
                // `key=` pair or the end of the block — the boundary rule quoted values
                // use. Cutting at the first bare `,`/`;` leaves a remainder that cannot
                // tokenize, which fails the whole block and leaks raw wire text.
                val separator = (cursor until end).firstOrNull { index ->
                    val character = rawMetadata[index]
                    (character == ';' || character == ',') &&
                        looksLikeFieldBoundary(rawMetadata, index + 1)
                }
                if (separator != null) {
                    value = rawMetadata.substring(cursor, separator)
                    cursor = separator
                } else {
                    value = rawMetadata.substring(cursor)
                    cursor = end
                }
            }
            fields[key] = value
        }

        if (fields.size < 2 && fields.keys.none(recognizedSingleFieldKeys::contains)) return null
        return fields
    }

    /**
     * The quote that actually closes a value: the earliest occurrence of [quote] after
     * which the remainder looks like a genuine field boundary, skipping any occurrence
     * that is part of the value itself.
     */
    private fun closingQuote(text: String, from: Int, quote: Char): Int? {
        var searchFrom = from
        while (true) {
            val index = text.indexOf(quote, searchFrom)
            if (index < 0) return null
            if (looksLikeFieldBoundary(text, index + 1)) return index
            searchFrom = index + 1
        }
    }

    /**
     * True when [index] is the end of the string, or is followed — after skipping any
     * separators and whitespace — by what looks like the start of the next `key=` pair.
     * Separators vary by dialect (`;`, `,`, or a bare space in Triton Digital's cue
     * metadata), so this checks structurally rather than for one fixed character.
     */
    private fun looksLikeFieldBoundary(text: String, index: Int): Boolean {
        val end = text.length
        var cursor = index
        while (cursor < end && isSeparator(text[cursor])) cursor++
        if (cursor == end) return true

        var keyEnd = cursor
        while (keyEnd < end && isKeyCharacter(text[keyEnd])) keyEnd++
        return keyEnd != cursor && keyEnd != end && text[keyEnd] == '='
    }

    private fun isSeparator(character: Char): Boolean =
        character == ';' || character == ',' || character.isWhitespace()

    private fun isKeyCharacter(character: Char): Boolean =
        character.isLetter() || character.isDigit() || character == '_'

    /**
     * The separator is searched in the raw string: trimming first would destroy a leading
     * separator in empty-artist titles like `" - Orphan Title"`, which is exactly the shape
     * that hides a nested cue block.
     */
    private fun splitArtistTitle(streamTitle: String): IcyTrack {
        val separator = streamTitle.indexOf(ARTIST_TITLE_SEPARATOR)
        if (separator < 0) {
            return IcyTrack(title = streamTitle.trim().takeUnless(String::isEmpty))
        }

        val artist = streamTitle.substring(0, separator).trim()
        val title = streamTitle.substring(separator + ARTIST_TITLE_SEPARATOR.length).trim()
        return IcyTrack(
            title = title.takeUnless(String::isEmpty),
            artist = artist.takeUnless(String::isEmpty),
        )
    }
}
