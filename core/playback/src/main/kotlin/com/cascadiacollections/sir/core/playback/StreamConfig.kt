package com.cascadiacollections.sir.core.playback

import androidx.annotation.StringRes

/**
 * Central stream configuration shared by the app, widget, tile and Wear modules, so the
 * stream URL is not duplicated across `RadioPlaybackService`, [StreamQuality] and
 * `WearPlaybackService`.
 */
object StreamConfig {

    /** A named stream preset offered in the debug stream override picker. */
    data class PresetStream(
        val name: String,
        val url: String,
    )

    /** Default SHOUTcast stream URL for SIR radio. */
    const val DEFAULT_STREAM_URL: String =
        "https://broadcast.shoutcheap.com/proxy/willradio/stream"

    /**
     * Manual debug preset streams for stream override testing. These are user-selected
     * presets and are never used as an automatic playback fallback.
     * Curated from https://github.com/mikepierce/internet-radio-streams.
     */
    val FALLBACK_TEST_STREAMS: List<PresetStream> = listOf(
        PresetStream(
            name = "Worldwide FM",
            url = "https://worldwide-fm.radiocult.fm/stream"
        ),
        PresetStream(
            name = "Subcity Radio",
            url = "https://stream.subcity.org/listen"
        ),
        PresetStream(
            name = "Le Mellotron",
            url = "https://listen.radioking.com/radio/477719/stream/534044"
        )
    )
}

/**
 * Stream quality options.
 *
 * All three currently point at the same SHOUTcast mount: the server auto-selects the
 * highest available bitrate for `/stream`. Add alternate mount paths here if the station
 * ever exposes them (e.g. `/stream_lo` for 64 kbps).
 */
enum class StreamQuality(@StringRes val labelRes: Int, val url: String) {
    HIGH(R.string.stream_quality_high, StreamConfig.DEFAULT_STREAM_URL),
    MEDIUM(R.string.stream_quality_medium, StreamConfig.DEFAULT_STREAM_URL),
    LOW(R.string.stream_quality_low, StreamConfig.DEFAULT_STREAM_URL);

    companion object {
        fun fromOrdinal(ordinal: Int): StreamQuality = entries.getOrNull(ordinal) ?: HIGH
    }
}
