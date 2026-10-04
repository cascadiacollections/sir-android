package com.cascadiacollections.sir.wear

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.WatchStationPayload
import com.cascadiacollections.sir.core.playback.StreamConfig

/** Station rules for the watch: the list it offers and how a station becomes a [MediaItem]. */
object WearStations {

    /** Media id of the built-in SIR stream, which has no radio-browser id. */
    const val DEFAULT_STATION_ID: String = "sir"

    /** Characters a SHORT_TEXT complication can show without truncation. */
    const val SHORT_TEXT_MAX: Int = 7

    fun default(name: String, url: String = StreamConfig.DEFAULT_STREAM_URL): Station =
        Station(id = DEFAULT_STATION_ID, name = name, url = url)

    /**
     * What the Recent Stations list shows: the SIR stream first, as on the phone's Auto
     * root, then the last-played station and the recents from the phone, de-duplicated.
     */
    fun list(default: Station, payload: WatchStationPayload): List<Station> =
        (listOf(default) + listOfNotNull(payload.last) + payload.recents)
            .filter { it.isPlayable }
            .distinctBy { it.id.ifBlank { it.streamUrl } }

    /**
     * The [MediaItem] for [station]: its resolved stream URL, an explicit HLS MIME type when
     * the directory flagged one (an `.m3u8` behind a redirect is otherwise probed as
     * progressive and fails), and the station name as the title.
     */
    fun mediaItem(station: Station, description: String): MediaItem = MediaItem.Builder()
        .setUri(station.streamUrl)
        .setMediaId(station.id.ifBlank { station.streamUrl })
        .apply { if (station.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(station.name.ifBlank { station.streamUrl })
                .setArtist(description)
                .setArtworkUri(station.favicon?.takeIf { it.isNotBlank() }?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()

    /**
     * [name] shortened for a SHORT_TEXT complication: as is when it fits, else its first
     * word when that fits, else truncated with an ellipsis.
     */
    fun abbreviate(name: String, max: Int = SHORT_TEXT_MAX): String {
        val trimmed = name.trim()
        if (trimmed.length <= max) return trimmed
        val firstWord = trimmed.substringBefore(' ')
        if (firstWord.length in 1..max) return firstWord
        return trimmed.take(max - 1).trimEnd() + "…"
    }
}
