package com.cascadiacollections.sir.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the phone tells the watch about the user's listening: the station last played and
 * the recently played stations, newest first. ShoutKit's watch app shows the same pair as
 * its "Play Last" complication and its Recent Stations list.
 *
 * Both fields default so a payload written by an older or newer phone build — a missing
 * field, or one this build doesn't know — still decodes.
 */
@Serializable
data class WatchStationPayload(
    val last: Station? = null,
    val recents: List<Station> = emptyList()
)

/**
 * The phone→watch wire format, shared by the Data Layer publisher in `:app` (Play flavor)
 * and the listener in `:wear`, so the two sides cannot drift apart.
 */
object WatchStationSync {

    /** Data Layer path of the single `DataItem` holding the payload. */
    const val PATH: String = "/sir/stations"

    /** `DataMap` key the JSON payload is stored under. */
    const val KEY_PAYLOAD: String = "payload"

    /** Recents beyond this are not sent: the watch list is a glance, not a library. */
    const val MAX_RECENTS: Int = 10

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Builds the payload: recents capped at [MAX_RECENTS], unplayable stations dropped. */
    fun payload(last: Station?, recents: List<Station>): WatchStationPayload =
        WatchStationPayload(
            last = last?.takeIf { it.isPlayable },
            recents = recents.filter { it.isPlayable }.distinctBy { it.id }.take(MAX_RECENTS)
        )

    fun encode(payload: WatchStationPayload): String = json.encodeToString(payload)

    /**
     * Total decode: anything unreadable — absent, truncated, or not JSON — is an empty
     * payload, because the watch must keep working (with its default stream) whatever the
     * phone sent.
     */
    fun decode(raw: String?): WatchStationPayload {
        if (raw.isNullOrBlank()) return WatchStationPayload()
        return runCatching { json.decodeFromString<WatchStationPayload>(raw) }
            .getOrDefault(WatchStationPayload())
    }
}
