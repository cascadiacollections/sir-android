package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Records every call so decorator pass-through and caching can be asserted. */
private class SpyDirectory : RadioDirectory {
    var stations: Result<List<Station>> = Result.success(emptyList())
    var tags: Result<List<Tag>> = Result.success(listOf(Tag("pop", 1)))
    var click: Result<Unit> = Result.success(Unit)

    val clicks = mutableListOf<String>()
    var tagCalls = 0
    val searchFilters = mutableListOf<StationSearchFilters>()
    val tagFilters = mutableListOf<StationSearchFilters>()

    override suspend fun search(query: StationQuery) = error("unfiltered overload must not be used")
    override suspend fun search(query: StationQuery, filters: StationSearchFilters): Result<List<Station>> {
        searchFilters += filters
        return stations
    }
    override suspend fun topStations(limit: Int) = stations
    override suspend fun stationsByTag(tag: String, limit: Int) = error("unfiltered overload must not be used")
    override suspend fun stationsByTag(tag: String, limit: Int, filters: StationSearchFilters): Result<List<Station>> {
        tagFilters += filters
        return stations
    }
    override suspend fun getStation(id: String): Result<Station?> = Result.success(null)
    override suspend fun topTags(limit: Int): Result<List<Tag>> {
        tagCalls++
        return tags.map { it.take(limit) }
    }
    override suspend fun reportClick(stationId: String): Result<Unit> {
        clicks += stationId
        return click
    }
}

class DirectoryDecoratorsTest {

    private val uuid = "96062a7b-0601-11e8-ae97-52543be04c81"

    @Test
    fun `clicks are never cached`() = runTest {
        val spy = SpyDirectory()
        val cache = CachingRadioDirectory(spy, clock = { 0L })

        repeat(3) { cache.reportClick(uuid) }

        assertEquals(listOf(uuid, uuid, uuid), spy.clicks)
    }

    @Test
    fun `click failures are not masked by the curated fallback`() = runTest {
        val spy = SpyDirectory().apply { click = Result.failure(IOException("down")) }
        val directory = CuratedFallbackDirectory(CachingRadioDirectory(spy))

        assertTrue(directory.reportClick(uuid).exceptionOrNull() is IOException)
        assertEquals(listOf(uuid), spy.clicks)
    }

    @Test
    fun `the full chain passes clicks through`() = runTest {
        val spy = SpyDirectory()
        val directory = CuratedFallbackDirectory(CachingRadioDirectory(spy))

        directory.reportClick(uuid)
        directory.reportClick(uuid)

        assertEquals(2, spy.clicks.size)
    }

    @Test
    fun `tags are cached for an hour`() = runTest {
        var now = 0L
        val spy = SpyDirectory()
        val cache = CachingRadioDirectory(spy, clock = { now })

        cache.topTags()
        now = CachingRadioDirectory.DEFAULT_TAG_TTL_MILLIS
        cache.topTags()
        assertEquals(1, spy.tagCalls)

        now += 1
        cache.topTags()
        assertEquals(2, spy.tagCalls)
    }

    @Test
    fun `tag failures are not cached`() = runTest {
        val spy = SpyDirectory().apply { tags = Result.failure(IOException("x")) }
        val cache = CachingRadioDirectory(spy, clock = { 0L })

        assertTrue(cache.topTags().isFailure)
        spy.tags = Result.success(listOf(Tag("rock", 2)))
        assertEquals(listOf(Tag("rock", 2)), cache.topTags().getOrThrow())
        assertEquals(2, spy.tagCalls)
    }

    @Test
    fun `invalidate drops cached tags`() = runTest {
        val spy = SpyDirectory()
        val cache = CachingRadioDirectory(spy, clock = { 0L })

        cache.topTags()
        cache.invalidate()
        cache.topTags()

        assertEquals(2, spy.tagCalls)
    }

    @Test
    fun `filters are part of the cache key`() = runTest {
        val spy = SpyDirectory()
        val cache = CachingRadioDirectory(spy, clock = { 0L })
        val gb = StationSearchFilters(countryCode = "GB")

        cache.search(StationQuery("jazz"))
        cache.search(StationQuery("jazz"), gb)
        cache.search(StationQuery("jazz"), StationSearchFilters(countryCode = "gb"))
        cache.stationsByTag("jazz", 10)
        cache.stationsByTag("jazz", 10, gb)
        cache.stationsByTag("JAZZ", 10, gb)

        assertEquals(listOf(StationSearchFilters.NONE, gb), spy.searchFilters)
        assertEquals(listOf(StationSearchFilters.NONE, gb), spy.tagFilters)
    }

    @Test
    fun `the convenience search forwards filters`() = runTest {
        val spy = SpyDirectory()
        val filters = StationSearchFilters(bitrateMinKbps = 128)

        spy.search("jazz", filters = filters)

        assertSame(filters, spy.searchFilters.single())
    }

    @Test
    fun `tags fall back to the curated genres`() = runTest {
        val spy = SpyDirectory().apply { tags = Result.failure(IOException("offline")) }
        val directory = CuratedFallbackDirectory(spy)

        assertEquals(Tag.CURATED, directory.topTags().getOrThrow())
        assertEquals(Tag.CURATED.take(5), directory.topTags(5).getOrThrow())
    }

    @Test
    fun `successful tags pass through the fallback`() = runTest {
        val directory = CuratedFallbackDirectory(SpyDirectory())

        assertEquals(listOf(Tag("pop", 1)), directory.topTags().getOrThrow())
    }

    @Test
    fun `curated station fallback honours filters`() = runTest {
        val spy = SpyDirectory().apply { stations = Result.failure(IOException("offline")) }
        val curated = listOf(
            Station(id = "a", name = "Jazz GB", url = "https://a", countryCode = "GB", tags = "jazz"),
            Station(id = "b", name = "Jazz US", url = "https://b", countryCode = "US", tags = "jazz")
        )
        val directory = CuratedFallbackDirectory(spy, curated = curated)
        val gb = StationSearchFilters(countryCode = "GB")

        assertEquals(listOf("a"), directory.search(StationQuery("jazz"), gb).getOrThrow().map { it.id })
        assertEquals(listOf("a"), directory.stationsByTag("jazz", 10, gb).getOrThrow().map { it.id })
    }

    @Test
    fun `interface defaults filter locally and report nothing`() = runTest {
        val plain = object : RadioDirectory {
            override suspend fun search(query: StationQuery) = Result.success(
                listOf(
                    Station(id = "lo", name = "lo", url = "https://lo", bitrate = 32),
                    Station(id = "hi", name = "hi", url = "https://hi", bitrate = 256)
                )
            )
            override suspend fun topStations(limit: Int) = Result.success(emptyList<Station>())
            override suspend fun stationsByTag(tag: String, limit: Int) = search(StationQuery(tag))
            override suspend fun getStation(id: String) = Result.success<Station?>(null)
        }
        val filters = StationSearchFilters(bitrateMinKbps = 128)

        assertEquals(listOf("hi"), plain.search(StationQuery("x"), filters).getOrThrow().map { it.id })
        assertEquals(listOf("hi"), plain.stationsByTag("x", 10, filters).getOrThrow().map { it.id })
        assertTrue(plain.reportClick(uuid).isSuccess)
        assertTrue(plain.topTags().getOrThrow().isEmpty())
    }
}
