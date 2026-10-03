package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Cleans station names as they arrive from radio-browser, ported from ShoutKit's
 * `StationNameFormatter`.
 *
 * Community-maintained entries use underscores for spaces and carry bracketed tags
 * (`[HD]`, `(128k)`, `(AAC)`) that describe the feed rather than the station. Applied
 * only when mapping API results — never to names the user typed or edited.
 */
object StationNameFormatter {

    /** A short bracketed or parenthesized clutter tag, e.g. `[HD]` or `(128k)`. */
    private val CLUTTER_TAG = Regex("""\[[^\]]{0,24}]|\([^)]{0,24}\)""")
    private val WHITESPACE = Regex("""\s+""")

    /** Normalized [raw], or [raw] trimmed if normalizing would leave nothing. */
    fun normalize(raw: String): String {
        val cleaned = raw.replace('_', ' ')
            .replace(CLUTTER_TAG, " ")
            .replace(WHITESPACE, " ")
            .trim()
        return cleaned.ifEmpty { raw.trim() }
    }

    /**
     * Upgrades a plain-http favicon to https and drops its explicit port — cleartext
     * image loads are blocked by the network security config, and an arbitrary
     * cleartext port (`:8080`) is unlikely to serve TLS. Best effort: a host without
     * TLS just shows the placeholder. Blank values become null; unparseable ones are
     * returned trimmed and untouched.
     */
    fun normalizeFavicon(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = trimmed.toHttpUrlOrNull() ?: return trimmed
        if (url.scheme != "http") return trimmed
        return url.newBuilder().scheme("https").port(HTTPS_PORT).build().toString()
    }

    private const val HTTPS_PORT = 443
}

/** Applies directory clean-up to a station decoded from the API. */
internal fun Station.normalizedFromDirectory(): Station = copy(
    name = StationNameFormatter.normalize(name),
    favicon = StationNameFormatter.normalizeFavicon(favicon)
)
