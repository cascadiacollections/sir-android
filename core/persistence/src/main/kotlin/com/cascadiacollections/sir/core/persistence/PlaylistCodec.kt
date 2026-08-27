package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station

/**
 * Parses and renders M3U/PLS playlist text for importing and exporting saved stations.
 *
 * Neither format carries an identifier comparable to radio-browser's `stationuuid`, so
 * imported stations derive [Station.id] from the URL itself — the one field guaranteed
 * present and stable across re-imports of the same file.
 */
object PlaylistCodec {

    private const val IMPORTED_ID_PREFIX = "imported:"

    /** Parses M3U/M3U8 text into stations. Blank and unrecognized lines are skipped. */
    fun parseM3u(text: String): List<Station> {
        val stations = mutableListOf<Station>()
        var pendingName: String? = null
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.equals("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingName = line.substringAfter(',', missingDelimiterValue = "").trim().ifBlank { null }
                }
                line.startsWith("#") -> Unit
                else -> {
                    stations += stationFor(url = line, name = pendingName)
                    pendingName = null
                }
            }
        }
        return stations
    }

    /** Parses PLS (`[playlist]` INI-style) text into stations. */
    fun parsePls(text: String): List<Station> {
        val urlsByIndex = mutableMapOf<Int, String>()
        val titlesByIndex = mutableMapOf<Int, String>()
        val entry = Regex("""^(File|Title)(\d+)=(.*)$""", RegexOption.IGNORE_CASE)

        text.lineSequence().forEach { rawLine ->
            val match = entry.matchEntire(rawLine.trim()) ?: return@forEach
            val (key, index, value) = match.destructured
            val i = index.toIntOrNull() ?: return@forEach
            if (key.equals("File", ignoreCase = true)) urlsByIndex[i] = value.trim()
            else titlesByIndex[i] = value.trim()
        }

        return urlsByIndex.toSortedMap().mapNotNull { (index, url) ->
            if (url.isBlank()) null else stationFor(url = url, name = titlesByIndex[index])
        }
    }

    /** Renders [stations] as M3U text: one `#EXTINF` + URL pair per station. */
    fun toM3u(stations: List<Station>): String = buildString {
        appendLine("#EXTM3U")
        stations.forEach { station ->
            appendLine("#EXTINF:-1,${station.name}")
            appendLine(station.url)
        }
    }

    private fun stationFor(url: String, name: String?): Station = Station(
        id = IMPORTED_ID_PREFIX + url,
        name = name?.ifBlank { null } ?: url.substringAfterLast('/').ifBlank { url },
        url = url
    )
}
