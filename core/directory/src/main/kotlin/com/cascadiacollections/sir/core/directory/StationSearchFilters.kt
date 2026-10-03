package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import java.util.Locale

/**
 * Optional refinements for a directory search, mirroring ShoutKit's
 * `StationSearchFilters`.
 *
 * Sent to radio-browser as `bitrateMin`/`bitrateMax`/`tagList`/`countrycode`, then
 * re-applied locally with [matches] because mirrors are not perfectly consistent in how
 * they honour those parameters. Locally, a station that is *missing* a value (bitrate 0,
 * no country, no tags) is never excluded — unknown is not a positive failure.
 *
 * Values are normalized on construction: blank strings become null, the tag is trimmed
 * and the country code upper-cased, and an inverted bitrate range is widened so it can
 * still match something.
 */
class StationSearchFilters(
    bitrateMinKbps: Int? = null,
    bitrateMaxKbps: Int? = null,
    tag: String? = null,
    countryCode: String? = null
) {
    val bitrateMinKbps: Int? = bitrateMinKbps?.takeIf { it > 0 }
    val bitrateMaxKbps: Int? = bitrateMaxKbps?.takeIf { it > 0 }?.let { max ->
        this.bitrateMinKbps?.let { min -> maxOf(min, max) } ?: max
    }
    val tag: String? = tag?.trim()?.takeIf { it.isNotEmpty() }
    val countryCode: String? = countryCode?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    /** Whether any filter is set. */
    val isActive: Boolean
        get() = bitrateMinKbps != null || bitrateMaxKbps != null || tag != null || countryCode != null

    /** Stable string used to key caches; equal filters produce equal keys. */
    val cacheKey: String
        get() = "${bitrateMinKbps ?: ""}-${bitrateMaxKbps ?: ""}-${tag?.lowercase(Locale.ROOT) ?: ""}-${countryCode ?: ""}"

    fun matches(station: Station): Boolean =
        matchesBitrate(station.bitrate) && matchesTag(station) && matchesCountry(station.countryCode)

    fun applyTo(stations: List<Station>): List<Station> =
        if (isActive) stations.filter(::matches) else stations

    /**
     * radio-browser query parameters for these filters. [includeTag] is false for genre
     * browse, which folds the filter tag into its own `tagList` instead.
     */
    fun queryParameters(includeTag: Boolean = true): List<Pair<String, String>> = buildList {
        bitrateMinKbps?.let { add("bitrateMin" to it.toString()) }
        bitrateMaxKbps?.let { add("bitrateMax" to it.toString()) }
        if (includeTag) tag?.let { add("tagList" to it.lowercase(Locale.ROOT)) }
        countryCode?.let { add("countrycode" to it) }
    }

    private fun matchesBitrate(bitrate: Int): Boolean {
        if (bitrate <= 0) return true
        if (bitrateMinKbps != null && bitrate < bitrateMinKbps) return false
        if (bitrateMaxKbps != null && bitrate > bitrateMaxKbps) return false
        return true
    }

    private fun matchesTag(station: Station): Boolean {
        val needle = tag?.lowercase(Locale.ROOT) ?: return true
        val tags = station.tagList
        if (tags.isEmpty()) return true
        return tags.any { it.lowercase(Locale.ROOT).contains(needle) }
    }

    private fun matchesCountry(stationCountry: String): Boolean {
        val wanted = countryCode ?: return true
        val actual = stationCountry.trim()
        if (actual.isEmpty()) return true
        return actual.equals(wanted, ignoreCase = true)
    }

    override fun equals(other: Any?): Boolean =
        other is StationSearchFilters && other.cacheKey == cacheKey

    override fun hashCode(): Int = cacheKey.hashCode()

    override fun toString(): String =
        "StationSearchFilters(bitrateMinKbps=$bitrateMinKbps, bitrateMaxKbps=$bitrateMaxKbps, " +
            "tag=$tag, countryCode=$countryCode)"

    companion object {
        /** No filtering. */
        val NONE: StationSearchFilters = StationSearchFilters()
    }
}
