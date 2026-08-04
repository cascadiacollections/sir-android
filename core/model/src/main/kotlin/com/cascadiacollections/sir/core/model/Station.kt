package com.cascadiacollections.sir.core.model

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
    val favicon: String? = null,
    val bitrate: Int = 0,
    val codec: String = "",
    @SerialName("countrycode")
    val countryCode: String = "",
    val tags: String = ""
) {
    /** Human readable label including codec/bitrate when the directory reported them. */
    val displayLabel: String
        get() = if (codec.isNotEmpty()) {
            "$name (${codec.uppercase()}, ${bitrate}kbps)"
        } else {
            name
        }

    /** Tags split into a trimmed, non-empty list. */
    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** A station is playable only when it carries a usable stream URL. */
    val isPlayable: Boolean
        get() = url.isNotBlank()
}
