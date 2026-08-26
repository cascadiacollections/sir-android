package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * In-memory, time-bounded cache in front of another [RadioDirectory].
 *
 * The directory API is rate limited and users re-issue the same queries constantly
 * (tab switches, rotation, back navigation), so a short TTL removes most traffic
 * without making results feel stale. Failures are never cached.
 */
class CachingRadioDirectory(
    private val delegate: RadioDirectory,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis
) : RadioDirectory {

    private data class Entry(val stations: List<Station>, val storedAt: Long)

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry>(0, 0.75f, true)

    override suspend fun search(query: StationQuery): Result<List<Station>> =
        cached("search:${query.normalizedText.lowercase(Locale.ROOT)}:${query.effectiveLimit}") {
            delegate.search(query)
        }

    override suspend fun topStations(limit: Int): Result<List<Station>> {
        val clamped = clampLimit(limit)
        return cached("top:$clamped") { delegate.topStations(clamped) }
    }

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> {
        val clamped = clampLimit(limit)
        return cached("tag:${tag.trim().lowercase(Locale.ROOT)}:$clamped") {
            delegate.stationsByTag(tag, clamped)
        }
    }

    override suspend fun getStation(id: String): Result<Station?> =
        cached("byuuid:$id") {
            delegate.getStation(id).map { station -> station?.let(::listOf) ?: emptyList() }
        }.map { it.firstOrNull() }

    /**
     * Applies the same clamp the network directory applies, so a caller asking for 1000
     * shares the cache entry with one asking for [StationQuery.MAX_LIMIT] instead of
     * storing a second copy of an identical response under its own key.
     */
    private fun clampLimit(limit: Int): Int = limit.coerceIn(1, StationQuery.MAX_LIMIT)

    /** Drops every cached entry, e.g. after a user-initiated refresh. */
    suspend fun invalidate() = mutex.withLock { entries.clear() }

    private suspend fun cached(
        key: String,
        load: suspend () -> Result<List<Station>>
    ): Result<List<Station>> {
        read(key)?.let { return Result.success(it) }

        return load().onSuccess { stations -> write(key, stations) }
    }

    private suspend fun read(key: String): List<Station>? = mutex.withLock {
        val entry = entries[key] ?: return@withLock null
        if (clock() - entry.storedAt > ttlMillis) {
            entries.remove(key)
            null
        } else {
            entry.stations
        }
    }

    private suspend fun write(key: String, stations: List<Station>) = mutex.withLock {
        entries[key] = Entry(stations, clock())
        while (entries.size > maxEntries) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 5 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES: Int = 32
    }
}
