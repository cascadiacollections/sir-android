package com.cascadiacollections.sir.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

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
    val favicon: String? = null,
    val bitrate: Int = 0,
    val codec: String = "",
    @SerialName("countrycode")
    val countryCode: String = "",
    val tags: String = ""
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

    /** A station is playable only when it carries a usable stream URL. */
    val isPlayable: Boolean
        get() = url.isNotBlank()
}
