package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station

/**
 * A small, hand-checked set of stations bundled with the app.
 *
 * These exist so browse/search surfaces are never completely empty when the
 * directory API is unreachable (offline, rate limited, mirror outage). Sources are
 * curated from https://github.com/mikepierce/internet-radio-streams.
 */
object CuratedStations {

    /** The station this app ships around; always first in curated results. */
    val SIR: Station = Station(
        id = "sir-default",
        name = "SIR Radio",
        url = "https://broadcast.shoutcheap.com/proxy/willradio/stream",
        bitrate = 128,
        codec = "MP3",
        tags = "community,indie"
    )

    val ALL: List<Station> = listOf(
        SIR,
        Station(
            id = "curated-worldwide-fm",
            name = "Worldwide FM",
            url = "https://worldwide-fm.radiocult.fm/stream",
            bitrate = 128,
            codec = "MP3",
            countryCode = "GB",
            tags = "eclectic,jazz,soul"
        ),
        Station(
            id = "curated-subcity",
            name = "Subcity Radio",
            url = "https://stream.subcity.org/listen",
            bitrate = 128,
            codec = "MP3",
            countryCode = "GB",
            tags = "college,eclectic"
        ),
        Station(
            id = "curated-le-mellotron",
            name = "Le Mellotron",
            url = "https://listen.radioking.com/radio/477719/stream/534044",
            bitrate = 128,
            codec = "MP3",
            countryCode = "FR",
            tags = "funk,soul,world"
        )
    )

    /**
     * Case-insensitive match over station names and tags.
     *
     * [stations] defaults to [ALL] but is a parameter so a caller configured with its own
     * curated set actually gets that set searched — previously this always filtered [ALL],
     * so an injected list was honoured by `topStations` and silently ignored by `search`.
     */
    fun matching(text: String, stations: List<Station> = ALL): List<Station> {
        val needle = text.trim().lowercase()
        if (needle.isEmpty()) return stations
        return stations.filter { station ->
            station.name.lowercase().contains(needle) ||
                station.tagList.any { it.lowercase().contains(needle) }
        }
    }
}
