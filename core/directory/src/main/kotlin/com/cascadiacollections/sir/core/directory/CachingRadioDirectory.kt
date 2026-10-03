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
    private val clock: () -> Long = System::currentTimeMillis,
    private val tagTtlMillis: Long = DEFAULT_TAG_TTL_MILLIS
) : RadioDirectory {

    private data class Entry(val stations: List<Station>, val storedAt: Long)
    private data class TagEntry(val tags: List<Tag>, val storedAt: Long)

    /** Keyed by limit; only a handful of distinct limits are ever requested. */
    private val tagEntries = HashMap<Int, TagEntry>()

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry>(0, 0.75f, true)

    override suspend fun search(query: StationQuery): Result<List<Station>> =
        search(query, StationSearchFilters.NONE)

    override suspend fun search(query: StationQuery, filters: StationSearchFilters): Result<List<Station>> =
        cached(
            "search:${query.normalizedText.lowercase(Locale.ROOT)}:${query.effectiveLimit}:${filters.cacheKey}"
        ) {
            delegate.search(query, filters)
        }

    override suspend fun topStations(limit: Int): Result<List<Station>> = topStations(limit, forceRefresh = false)

    /**
     * A forced refresh skips the lookup but still stores a success, so the fresh answer is
     * what the next ordinary call (rotation, tab switch) sees.
     */
    override suspend fun topStations(limit: Int, forceRefresh: Boolean): Result<List<Station>> {
        val clamped = clampLimit(limit)
        return cached("top:$clamped", bypass = forceRefresh) { delegate.topStations(clamped, forceRefresh) }
    }

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> =
        stationsByTag(tag, limit, StationSearchFilters.NONE)

    override suspend fun stationsByTag(
        tag: String,
        limit: Int,
        filters: StationSearchFilters
    ): Result<List<Station>> {
        val clamped = clampLimit(limit)
        return cached("tag:${tag.trim().lowercase(Locale.ROOT)}:$clamped:${filters.cacheKey}") {
            delegate.stationsByTag(tag, clamped, filters)
        }
    }

    /**
     * Tags change slowly — station counts drift, the ranking barely moves — so they get
     * their own, much longer TTL instead of sharing the station entries' five minutes.
     */
    override suspend fun topTags(limit: Int): Result<List<Tag>> = topTags(limit, forceRefresh = false)

    override suspend fun topTags(limit: Int, forceRefresh: Boolean): Result<List<Tag>> {
        val key = limit.coerceIn(1, RadioBrowserDirectory.MAX_TAG_LIMIT)
        if (!forceRefresh) {
            mutex.withLock {
                val entry = tagEntries[key]
                if (entry != null && clock() - entry.storedAt <= tagTtlMillis) return Result.success(entry.tags)
            }
        }
        return delegate.topTags(key, forceRefresh).onSuccess { tags ->
            mutex.withLock { tagEntries[key] = TagEntry(tags, clock()) }
        }
    }

    /** Never cached: every explicit play must reach radio-browser to be counted. */
    override suspend fun reportClick(stationId: String): Result<Unit> = delegate.reportClick(stationId)

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
    suspend fun invalidate() = mutex.withLock {
        entries.clear()
        tagEntries.clear()
    }

    private suspend fun cached(
        key: String,
        bypass: Boolean = false,
        load: suspend () -> Result<List<Station>>
    ): Result<List<Station>> {
        if (!bypass) read(key)?.let { return Result.success(it) }

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
        const val DEFAULT_TAG_TTL_MILLIS: Long = 60 * 60 * 1000L
    }
}
