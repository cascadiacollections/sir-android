package com.cascadiacollections.sir.core.playback

/** What a fetched station playlist turned out to contain. */
sealed interface PlaylistContent {
    /** The first stream entry the playlist points at. */
    data class Entry(val url: String) : PlaylistContent

    /**
     * The body is itself an HLS playlist (`#EXT-X-` tags) served under a `.m3u` name, so
     * the original URL is the stream and must be opened as HLS rather than followed.
     */
    data object Hls : PlaylistContent

    /** No usable `http(s)` entry — including a body that was not a playlist at all. */
    data object Empty : PlaylistContent
}

/**
 * Extracts the stream URL from a station's PLS or M3U playlist.
 *
 * Unlike `PlaylistCodec` in `:core:persistence`, which imports every entry as a saved
 * station, this only needs the first playable entry, and has to tell a pointer playlist
 * apart from an HLS one. The format is sniffed from the body rather than trusted from
 * the extension, because servers routinely mislabel the two.
 */
object StreamPlaylistParser {

    private val plsEntry = Regex("""^File(\d+)\s*=\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): PlaylistContent {
        val lines = text.removePrefix("﻿").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.any { it.startsWith("#EXT-X-", ignoreCase = true) }) return PlaylistContent.Hls

        val plsUrls = lines.mapNotNull { line ->
            val match = plsEntry.matchEntire(line) ?: return@mapNotNull null
            val index = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            index to match.groupValues[2].trim()
        }.sortedBy { it.first }.map { it.second }

        val candidates = plsUrls.ifEmpty { lines.filterNot { it.startsWith("#") } }
        return candidates.firstOrNull(::isHttpUrl)?.let(PlaylistContent::Entry) ?: PlaylistContent.Empty
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)
}
