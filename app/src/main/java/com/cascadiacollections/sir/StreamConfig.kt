package com.cascadiacollections.sir

/**
 * Central stream configuration constants shared across the app, widget, tile, and wear modules.
 * Eliminates duplication of the stream URL across RadioPlaybackService, StreamQuality, and WearPlaybackService.
 */
object StreamConfig {
    /** Default SHOUTcast stream URL for SIR radio */
    const val DEFAULT_STREAM_URL = "https://broadcast.shoutcheap.com/proxy/willradio/stream"
}

