package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isSuccess
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class RecordingDirectory(private var result: Result<List<Station>> = Result.success(emptyList())) :
    RadioDirectory {
    var searchCalls = 0
        private set
    var topCalls = 0
        private set

    fun respondWith(next: Result<List<Station>>) {
        result = next
    }

    override suspend fun search(query: StationQuery): Result<List<Station>> {
        searchCalls++
        return result
    }

    var lastTopLimit: Int? = null
        private set

    override suspend fun topStations(limit: Int): Result<List<Station>> {
        topCalls++
        lastTopLimit = limit
        return result
    }

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> = result

    var getStationCalls = 0
        private set

    override suspend fun getStation(id: String): Result<Station?> {
        getStationCalls++
        return result.map { it.firstOrNull { s -> s.id == id } }
    }
}

class CachingRadioDirectoryTest {

    private val station = Station(id = "1", name = "Test", url = "https://example.com/s")

    @Test
    fun `repeated search within ttl hits delegate once`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, ttlMillis = 1_000, clock = { 0L })

        repeat(3) { cache.search(StationQuery("jazz")) }

        assertThat(delegate.searchCalls).isEqualTo(1)
    }

    @Test
    fun `search is cached case-insensitively`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, clock = { 0L })

        cache.search(StationQuery("Jazz"))
        cache.search(StationQuery("jAZZ"))

        assertThat(delegate.searchCalls).isEqualTo(1)
    }

    @Test
    fun `expired entry is refetched`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        var now = 0L
        val cache = CachingRadioDirectory(delegate, ttlMillis = 100, clock = { now })

        cache.search(StationQuery("jazz"))
        now = 500
        cache.search(StationQuery("jazz"))

        assertThat(delegate.searchCalls).isEqualTo(2)
    }

    @Test
    fun `failures are not cached`() = runTest {
        val delegate = RecordingDirectory(Result.failure(IOException("boom")))
        val cache = CachingRadioDirectory(delegate, clock = { 0L })

        assertThat(cache.topStations(10)).isFailure()
        delegate.respondWith(Result.success(listOf(station)))

        assertThat(cache.topStations(10)).isSuccess().containsExactly(station)
        assertThat(delegate.topCalls).isEqualTo(2)
    }

    @Test
    fun `invalidate drops cached entries`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, clock = { 0L })

        cache.search(StationQuery("jazz"))
        cache.invalidate()
        cache.search(StationQuery("jazz"))

        assertThat(delegate.searchCalls).isEqualTo(2)
    }

    @Test
    fun `cache evicts least recently used beyond max entries`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, maxEntries = 2, clock = { 0L })

        cache.search(StationQuery("a"))
        cache.search(StationQuery("b"))
        cache.search(StationQuery("a"))
        cache.search(StationQuery("c"))
        assertThat(delegate.searchCalls).isEqualTo(3)

        // "b" was evicted, "a" was refreshed by the third call and must still be cached.
        cache.search(StationQuery("a"))
        assertThat(delegate.searchCalls).isEqualTo(3)

        cache.search(StationQuery("b"))
        assertThat(delegate.searchCalls).isEqualTo(4)
    }

    @Test
    fun `limits beyond the maximum share one cache entry`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, clock = { 0L })

        // The network directory clamps to MAX_LIMIT, so these are the same request and
        // must not be stored under two keys.
        cache.topStations(1_000)
        cache.topStations(StationQuery.MAX_LIMIT)

        assertThat(delegate.topCalls).isEqualTo(1)
        assertThat(delegate.lastTopLimit).isEqualTo(StationQuery.MAX_LIMIT)
    }
}
