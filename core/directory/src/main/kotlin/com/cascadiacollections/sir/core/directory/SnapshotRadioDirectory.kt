package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists discovery ([topStations], [topTags]) across launches, as ShoutKit's
 * `DirectoryDiscoverySnapshot` does, so the browse tab paints instantly on a cold start and
 * still has real stations offline.
 *
 * Policy, per section and only when the stored section was fetched with the same limit:
 * - younger than [maxFreshAgeMillis] (6 h): answered from the snapshot, no request at all;
 * - older: answered from the snapshot at once, and refreshed in [backgroundScope]
 *   (stale-while-revalidate). A success replaces the snapshot and is announced on
 *   [discoveryUpdates] so a screen showing the old answer can swap it in;
 * - `forceRefresh` (pull-to-refresh, the background worker): always the network, and a
 *   success replaces the snapshot.
 *
 * With no usable snapshot the call goes to [delegate] and a non-empty success is stored.
 * Failures and empty answers are never stored, so they cannot replace good data.
 *
 * Sits between the in-memory cache and the curated fallback: the cache below it keeps a
 * revalidation from duplicating a request made moments earlier, and the fallback above it
 * still answers a cold, offline first launch that has no snapshot yet. Search, genre
 * browse and lookups pass straight through and are never persisted.
 */
class SnapshotRadioDirectory(
    private val delegate: RadioDirectory,
    private val store: DiscoverySnapshotStore,
    private val backgroundScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxFreshAgeMillis: Long = DEFAULT_MAX_FRESH_AGE_MILLIS
) : RadioDirectory {

    private val mutex = Mutex()
    private var snapshot: DiscoverySnapshot? = null

    /** Sections with a background refresh in flight; not mutex-guarded so `finally` can clear it when cancelled. */
    private val revalidating: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val updates = MutableSharedFlow<DiscoveryUpdate>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val discoveryUpdates: Flow<DiscoveryUpdate> =
        merge(updates.asSharedFlow(), delegate.discoveryUpdates)

    override suspend fun topStations(limit: Int): Result<List<Station>> = topStations(limit, forceRefresh = false)

    override suspend fun topStations(limit: Int, forceRefresh: Boolean): Result<List<Station>> {
        val clamped = limit.coerceIn(1, StationQuery.MAX_LIMIT)
        if (forceRefresh) return fetchTopStations(clamped, forceRefresh = true, announce = true)

        val section = current().topStations?.takeIf { it.limit == clamped }
            ?: return fetchTopStations(clamped, forceRefresh = false, announce = false)
        if (!isFresh(section.savedAtMillis)) {
            revalidate("top:$clamped") {
                fetchTopStations(clamped, forceRefresh = false, announce = true)
            }
        }
        return Result.success(section.stations)
    }

    override suspend fun topTags(limit: Int): Result<List<Tag>> = topTags(limit, forceRefresh = false)

    override suspend fun topTags(limit: Int, forceRefresh: Boolean): Result<List<Tag>> {
        val clamped = limit.coerceIn(1, RadioBrowserDirectory.MAX_TAG_LIMIT)
        if (forceRefresh) return fetchTopTags(clamped, forceRefresh = true, announce = true)

        val section = current().topTags?.takeIf { it.limit == clamped }
            ?: return fetchTopTags(clamped, forceRefresh = false, announce = false)
        if (!isFresh(section.savedAtMillis)) {
            revalidate("tags:$clamped") {
                fetchTopTags(clamped, forceRefresh = false, announce = true)
            }
        }
        return Result.success(section.tags)
    }

    override suspend fun search(query: StationQuery): Result<List<Station>> = search(query, StationSearchFilters.NONE)

    override suspend fun search(query: StationQuery, filters: StationSearchFilters): Result<List<Station>> =
        delegate.search(query, filters)

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> =
        stationsByTag(tag, limit, StationSearchFilters.NONE)

    override suspend fun stationsByTag(tag: String, limit: Int, filters: StationSearchFilters): Result<List<Station>> =
        delegate.stationsByTag(tag, limit, filters)

    override suspend fun getStation(id: String): Result<Station?> = delegate.getStation(id)

    override suspend fun getStations(ids: List<String>): Result<List<Station>> = delegate.getStations(ids)

    override suspend fun reportClick(stationId: String): Result<Unit> = delegate.reportClick(stationId)

    /** A clock that moved backwards makes the age negative; that is treated as stale. */
    private fun isFresh(savedAtMillis: Long): Boolean = (clock() - savedAtMillis) in 0 until maxFreshAgeMillis

    private suspend fun fetchTopStations(limit: Int, forceRefresh: Boolean, announce: Boolean): Result<List<Station>> =
        delegate.topStations(limit, forceRefresh).onSuccess { stations ->
            if (stations.isEmpty()) return@onSuccess
            persist {
                it.copy(topStations = DiscoverySnapshot.StationsSection(limit, clock(), stations))
            }
            if (announce) updates.tryEmit(DiscoveryUpdate.TopStations(limit, stations))
        }

    private suspend fun fetchTopTags(limit: Int, forceRefresh: Boolean, announce: Boolean): Result<List<Tag>> =
        delegate.topTags(limit, forceRefresh).onSuccess { tags ->
            if (tags.isEmpty()) return@onSuccess
            persist { it.copy(topTags = DiscoverySnapshot.TagsSection(limit, clock(), tags)) }
            if (announce) updates.tryEmit(DiscoveryUpdate.TopTags(limit, tags))
        }

    /** Loads the snapshot once per process; an unreadable one counts as empty. */
    private suspend fun current(): DiscoverySnapshot = mutex.withLock { loadedLocked() }

    private suspend fun loadedLocked(): DiscoverySnapshot =
        snapshot ?: (store.read() ?: DiscoverySnapshot()).also { snapshot = it }

    /**
     * Applies [change] in memory and writes the result. A failed write keeps the in-memory
     * copy, so this process still benefits; the next success tries the disk again.
     */
    private suspend fun persist(change: (DiscoverySnapshot) -> DiscoverySnapshot) = mutex.withLock {
        val updated = change(loadedLocked())
        snapshot = updated
        try {
            store.write(updated)
        } catch (_: IOException) {
            // Disk full or the directory vanished: nothing to do but keep serving memory.
        }
    }

    /** At most one background refresh per section at a time. */
    private fun revalidate(key: String, refresh: suspend () -> Unit) {
        if (!revalidating.add(key)) return
        backgroundScope.launch {
            try {
                refresh()
            } finally {
                revalidating.remove(key)
            }
        }
    }

    companion object {
        /** ShoutKit's discovery snapshot freshness window. */
        const val DEFAULT_MAX_FRESH_AGE_MILLIS: Long = 6 * 60 * 60 * 1000L
    }
}
