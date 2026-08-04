package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class RecordingDirectory(
    private var result: Result<List<Station>> = Result.success(emptyList())
) : RadioDirectory {
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

    override suspend fun topStations(limit: Int): Result<List<Station>> {
        topCalls++
        return result
    }

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> = result
}

class CachingRadioDirectoryTest {

    private val station = Station(id = "1", name = "Test", url = "https://example.com/s")

    @Test
    fun `repeated search within ttl hits delegate once`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, ttlMillis = 1_000, clock = { 0L })

        repeat(3) { cache.search(StationQuery("jazz")) }

        assertEquals(1, delegate.searchCalls)
    }

    @Test
    fun `search is cached case-insensitively`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, clock = { 0L })

        cache.search(StationQuery("Jazz"))
        cache.search(StationQuery("jAZZ"))

        assertEquals(1, delegate.searchCalls)
    }

    @Test
    fun `expired entry is refetched`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        var now = 0L
        val cache = CachingRadioDirectory(delegate, ttlMillis = 100, clock = { now })

        cache.search(StationQuery("jazz"))
        now = 500
        cache.search(StationQuery("jazz"))

        assertEquals(2, delegate.searchCalls)
    }

    @Test
    fun `failures are not cached`() = runTest {
        val delegate = RecordingDirectory(Result.failure(IOException("boom")))
        val cache = CachingRadioDirectory(delegate, clock = { 0L })

        assertTrue(cache.topStations(10).isFailure)
        delegate.respondWith(Result.success(listOf(station)))

        assertEquals(listOf(station), cache.topStations(10).getOrNull())
        assertEquals(2, delegate.topCalls)
    }

    @Test
    fun `invalidate drops cached entries`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, clock = { 0L })

        cache.search(StationQuery("jazz"))
        cache.invalidate()
        cache.search(StationQuery("jazz"))

        assertEquals(2, delegate.searchCalls)
    }

    @Test
    fun `cache evicts least recently used beyond max entries`() = runTest {
        val delegate = RecordingDirectory(Result.success(listOf(station)))
        val cache = CachingRadioDirectory(delegate, maxEntries = 2, clock = { 0L })

        cache.search(StationQuery("a"))
        cache.search(StationQuery("b"))
        cache.search(StationQuery("a"))
        cache.search(StationQuery("c"))
        assertEquals(3, delegate.searchCalls)

        // "b" was evicted, "a" was refreshed by the third call and must still be cached.
        cache.search(StationQuery("a"))
        assertEquals(3, delegate.searchCalls)

        cache.search(StationQuery("b"))
        assertEquals(4, delegate.searchCalls)
    }
}
