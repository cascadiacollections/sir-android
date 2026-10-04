package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isSuccess
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
    var topCalls = 0
    override suspend fun topStations(limit: Int): Result<List<Station>> {
        topCalls++
        return stations
    }
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

        assertThat(spy.clicks).containsExactly(uuid, uuid, uuid)
    }

    @Test
    fun `click failures are not masked by the curated fallback`() = runTest {
        val spy = SpyDirectory().apply { click = Result.failure(IOException("down")) }
        val directory = CuratedFallbackDirectory(CachingRadioDirectory(spy))

        assertThat(directory.reportClick(uuid)).isFailure().isInstanceOf<IOException>()
        assertThat(spy.clicks).containsExactly(uuid)
    }

    @Test
    fun `the full chain passes clicks through`() = runTest {
        val spy = SpyDirectory()
        val directory = CuratedFallbackDirectory(CachingRadioDirectory(spy))

        directory.reportClick(uuid)
        directory.reportClick(uuid)

        assertThat(spy.clicks).hasSize(2)
    }

    @Test
    fun `tags are cached for an hour`() = runTest {
        var now = 0L
        val spy = SpyDirectory()
        val cache = CachingRadioDirectory(spy, clock = { now })

        cache.topTags()
        now = CachingRadioDirectory.DEFAULT_TAG_TTL_MILLIS
        cache.topTags()
        assertThat(spy.tagCalls).isEqualTo(1)

        now += 1
        cache.topTags()
        assertThat(spy.tagCalls).isEqualTo(2)
    }

    @Test
    fun `tag failures are not cached`() = runTest {
        val spy = SpyDirectory().apply { tags = Result.failure(IOException("x")) }
        val cache = CachingRadioDirectory(spy, clock = { 0L })

        assertThat(cache.topTags()).isFailure()
        spy.tags = Result.success(listOf(Tag("rock", 2)))
        assertThat(cache.topTags().getOrThrow()).containsExactly(Tag("rock", 2))
        assertThat(spy.tagCalls).isEqualTo(2)
    }

    @Test
    fun `invalidate drops cached tags`() = runTest {
        val spy = SpyDirectory()
        val cache = CachingRadioDirectory(spy, clock = { 0L })

        cache.topTags()
        cache.invalidate()
        cache.topTags()

        assertThat(spy.tagCalls).isEqualTo(2)
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

        assertThat(spy.searchFilters).containsExactly(StationSearchFilters.NONE, gb)
        assertThat(spy.tagFilters).containsExactly(StationSearchFilters.NONE, gb)
    }

    @Test
    fun `the convenience search forwards filters`() = runTest {
        val spy = SpyDirectory()
        val filters = StationSearchFilters(bitrateMinKbps = 128)

        spy.search("jazz", filters = filters)

        assertThat(spy.searchFilters.single()).isSameInstanceAs(filters)
    }

    @Test
    fun `tags fall back to the curated genres`() = runTest {
        val spy = SpyDirectory().apply { tags = Result.failure(IOException("offline")) }
        val directory = CuratedFallbackDirectory(spy)

        assertThat(directory.topTags().getOrThrow()).isEqualTo(Tag.CURATED)
        assertThat(directory.topTags(5).getOrThrow()).isEqualTo(Tag.CURATED.take(5))
    }

    @Test
    fun `successful tags pass through the fallback`() = runTest {
        val directory = CuratedFallbackDirectory(SpyDirectory())

        assertThat(directory.topTags().getOrThrow()).containsExactly(Tag("pop", 1))
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

        assertThat(directory.search(StationQuery("jazz"), gb).getOrThrow().map { it.id })
            .containsExactly("a")
        assertThat(directory.stationsByTag("jazz", 10, gb).getOrThrow().map { it.id })
            .containsExactly("a")
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

        assertThat(plain.search(StationQuery("x"), filters).getOrThrow().map { it.id })
            .containsExactly("hi")
        assertThat(plain.stationsByTag("x", 10, filters).getOrThrow().map { it.id })
            .containsExactly("hi")
        assertThat(plain.reportClick(uuid)).isSuccess()
        assertThat(plain.topTags().getOrThrow()).isEmpty()
    }

    @Test
    fun `a forced refresh bypasses cached top stations and refreshes the entry`() = runTest {
        val old = listOf(Station(id = "old", name = "Old", url = "https://old"))
        val new = listOf(Station(id = "new", name = "New", url = "https://new"))
        val spy = SpyDirectory().apply { stations = Result.success(old) }
        val cache = CachingRadioDirectory(spy, clock = { 0L })

        cache.topStations(24)
        cache.topStations(24)
        assertThat(spy.topCalls).isEqualTo(1)

        spy.stations = Result.success(new)
        assertThat(cache.topStations(24, forceRefresh = true).getOrThrow()).isEqualTo(new)
        assertThat(spy.topCalls).isEqualTo(2)

        // The refreshed answer is what the next ordinary call is served from.
        assertThat(cache.topStations(24).getOrThrow()).isEqualTo(new)
        assertThat(spy.topCalls).isEqualTo(2)
    }

    @Test
    fun `a failed forced refresh keeps the cached top stations`() = runTest {
        val old = listOf(Station(id = "old", name = "Old", url = "https://old"))
        val spy = SpyDirectory().apply { stations = Result.success(old) }
        val cache = CachingRadioDirectory(spy, clock = { 0L })
        cache.topStations(24)

        spy.stations = Result.failure(IOException("offline"))
        assertThat(cache.topStations(24, forceRefresh = true)).isFailure()
        assertThat(cache.topStations(24).getOrThrow()).isEqualTo(old)
    }

    @Test
    fun `a forced refresh bypasses cached tags`() = runTest {
        val spy = SpyDirectory()
        val cache = CachingRadioDirectory(spy, clock = { 0L })

        cache.topTags(10)
        cache.topTags(10)
        cache.topTags(10, forceRefresh = true)

        assertThat(spy.tagCalls).isEqualTo(2)
    }

    @Test
    fun `the curated fallback does not mask a forced refresh failure`() = runTest {
        val spy = SpyDirectory().apply {
            stations = Result.failure(IOException("offline"))
            tags = Result.failure(IOException("offline"))
        }
        val curated = listOf(Station(id = "c", name = "Curated", url = "https://c"))
        val directory = CuratedFallbackDirectory(CachingRadioDirectory(spy), curated = curated)

        assertThat(directory.topStations(24).getOrThrow()).isEqualTo(curated)
        assertThat(directory.topStations(24, forceRefresh = false).getOrThrow()).isEqualTo(curated)
        assertThat(directory.topStations(24, forceRefresh = true)).isFailure().isInstanceOf<IOException>()
        assertThat(directory.topTags(10)).isSuccess()
        assertThat(directory.topTags(10, forceRefresh = true)).isFailure().isInstanceOf<IOException>()
    }

    @Test
    fun `the forced refresh overloads default to the plain calls`() = runTest {
        val spy = SpyDirectory()
        val plain = object : RadioDirectory {
            override suspend fun search(query: StationQuery) = Result.success(emptyList<Station>())
            override suspend fun topStations(limit: Int) = spy.topStations(limit)
            override suspend fun stationsByTag(tag: String, limit: Int) = Result.success(emptyList<Station>())
            override suspend fun getStation(id: String) = Result.success<Station?>(null)
            override suspend fun topTags(limit: Int) = spy.topTags(limit)
        }

        plain.topStations(5, forceRefresh = true)
        plain.topTags(5, forceRefresh = true)

        assertThat(spy.topCalls).isEqualTo(1)
        assertThat(spy.tagCalls).isEqualTo(1)
    }
}
