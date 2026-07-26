package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import kotlinx.serialization.json.Json

/**
 * Encodes/decodes station collections for key-value storage.
 *
 * Decoding is deliberately total: persisted preferences are user data that can be
 * truncated by an interrupted write or written by an older build, and losing the
 * user's favourites is far worse than dropping an unreadable blob.
 */
object StationCodec {

    private val json = Json { ignoreUnknownKeys = true }

    const val EMPTY: String = "[]"

    fun decode(raw: String?): List<Station> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Station>>(raw) }.getOrDefault(emptyList())
    }

    fun encode(stations: List<Station>): String = json.encodeToString(stations)
}
