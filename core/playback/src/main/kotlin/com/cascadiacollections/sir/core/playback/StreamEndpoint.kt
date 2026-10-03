package com.cascadiacollections.sir.core.playback

import java.net.URI
import java.util.Locale

/** How a stream URL has to be handled before the player can be pointed at it. */
enum class StreamUrlKind {
    /** A progressive stream (Icecast/Shoutcast, plain file) the player opens as-is. */
    DIRECT,

    /** An HLS playlist; the player needs the HLS MIME type to pick the right source. */
    HLS,

    /**
     * A `.pls` or `.m3u` playlist whose entries point at the real stream. Imported and
     * custom stations, and directory entries without `url_resolved`, often carry one.
     */
    PLAYLIST,
}

/** The URL the player should actually open, and whether it is HLS. */
data class StreamEndpoint(val url: String, val isHls: Boolean = false)

/**
 * Decides what the player is given for a station URL.
 *
 * Classification is by URL path, ignoring query and fragment, because that is all that
 * is known before a request is made; radio-browser's `hls` flag is honoured when the
 * directory supplied it. A playlist is fetched by the caller and its body handed to
 * [fromPlaylist], which keeps this decision free of any HTTP or player dependency.
 */
object StreamEndpoints {

    fun classify(url: String, hlsHint: Boolean = false): StreamUrlKind {
        if (hlsHint) return StreamUrlKind.HLS
        val path = pathOf(url).lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") -> StreamUrlKind.HLS
            path.endsWith(".pls") || path.endsWith(".m3u") -> StreamUrlKind.PLAYLIST
            else -> StreamUrlKind.DIRECT
        }
    }

    /**
     * The endpoint for [url] when it can be decided without a network round trip, or
     * null when [url] is a playlist that has to be fetched first.
     */
    fun withoutFetch(url: String, hlsHint: Boolean = false): StreamEndpoint? =
        when (classify(url, hlsHint)) {
            StreamUrlKind.DIRECT -> StreamEndpoint(url)
            StreamUrlKind.HLS -> StreamEndpoint(url, isHls = true)
            StreamUrlKind.PLAYLIST -> null
        }

    /**
     * The endpoint for playlist [url] given its fetched [body] — null when the fetch
     * failed. Anything that does not yield a stream entry falls back to [url] itself:
     * playing the original is no worse than before resolution existed, and some servers
     * answer a `.m3u` path with the audio stream directly.
     */
    fun fromPlaylist(url: String, body: String?): StreamEndpoint =
        when (val content = body?.let(StreamPlaylistParser::parse)) {
            is PlaylistContent.Entry -> StreamEndpoint(
                url = content.url,
                isHls = classify(content.url) == StreamUrlKind.HLS
            )
            PlaylistContent.Hls -> StreamEndpoint(url, isHls = true)
            PlaylistContent.Empty, null -> StreamEndpoint(url)
        }

    private fun pathOf(url: String): String {
        val parsed = try {
            URI(url.trim()).rawPath
        } catch (_: java.net.URISyntaxException) {
            null
        }
        return parsed ?: url.substringBefore('#').substringBefore('?')
    }
}
