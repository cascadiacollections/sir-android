package com.cascadiacollections.sir.core.model

import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Platform-neutral radio station.
 *
 * Serial names intentionally match the radio-browser.info API payload so the same
 * type can be decoded directly from the directory API and from previously persisted
 * user data without a migration.
 */
@Serializable
data class Station(
    @SerialName("stationuuid")
    val id: String = "",
    val name: String = "",
    val url: String = "",
    /**
     * The directory's resolved stream endpoint. radio-browser follows a station's
     * playlist/redirect chain server-side and publishes the final URL here, so it is
     * preferred over [url] for playback. Absent from data persisted before it existed,
     * and from imported/custom stations, hence the empty default.
     */
    @SerialName("url_resolved")
    val urlResolved: String = "",
    val favicon: String? = null,
    val bitrate: Int = 0,
    val codec: String = "",
    @SerialName("countrycode")
    val countryCode: String = "",
    val tags: String = "",
    /** radio-browser's HLS flag: 1 when the stream is an HLS (`.m3u8`) playlist. */
    val hls: Int = 0
) {
    /**
     * Human readable label including codec/bitrate when the directory reported them.
     *
     * radio-browser leaves `bitrate` at 0 for plenty of entries, so it is only shown
     * when positive — otherwise the label claimed "0kbps", which reads as a broken
     * stream rather than as an unknown value.
     */
    val displayLabel: String
        get() {
            if (codec.isEmpty()) return name
            // Locale.ROOT: a codec is a format token, not prose. Under a Turkish locale
            // the default uppercase() maps 'i' to the dotted 'İ', so "vorbis" rendered as
            // "VORBİS" on those devices and as "VORBIS" everywhere else.
            val codecLabel = codec.uppercase(Locale.ROOT)
            return if (bitrate > 0) "$name ($codecLabel, ${bitrate}kbps)" else "$name ($codecLabel)"
        }

    /** Tags split into a trimmed, non-empty list. */
    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** The URL playback should start from: [urlResolved] when known, otherwise [url]. */
    val streamUrl: String
        get() = urlResolved.ifBlank { url }

    /** Whether the directory reported this station as an HLS stream. */
    val isHls: Boolean
        get() = hls == 1

    /** A station is playable only when it carries a usable stream URL. */
    val isPlayable: Boolean
        get() = streamUrl.isNotBlank()

    /**
     * This station pointed at [newUrl]. [urlResolved] and [hls] describe the directory's
     * view of the *old* URL, so they are dropped when it changes — otherwise an edited
     * station would keep playing the stream it was edited away from.
     */
    fun withUrl(newUrl: String): Station = if (newUrl == url) this else copy(url = newUrl, urlResolved = "", hls = 0)
}
