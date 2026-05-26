package com.cascadiacollections.sir

data class StreamSource(
    val name: String,
    val url: String
)

/**
 * Central stream configuration constants shared across the app, widget, tile, and wear modules.
 * Eliminates duplication of the stream URL across RadioPlaybackService, StreamQuality, and WearPlaybackService.
 */
object StreamConfig {
    /** Default SHOUTcast stream URL for SIR radio */
    const val DEFAULT_STREAM_URL = "https://broadcast.shoutcheap.com/proxy/willradio/stream"

    /**
     * Free fallback streams for playback testing when the primary station is unavailable.
     * Sources are curated from https://github.com/mikepierce/internet-radio-streams.
     */
    val FALLBACK_TEST_STREAMS: List<StreamSource> = listOf(
        StreamSource(
            name = "Worldwide FM",
            url = "https://worldwide-fm.radiocult.fm/stream"
        ),
        StreamSource(
            name = "Subcity Radio",
            url = "https://stream.subcity.org/listen"
        ),
        StreamSource(
            name = "Le Mellotron",
            url = "https://listen.radioking.com/radio/477719/stream/534044"
        )
    )
}
