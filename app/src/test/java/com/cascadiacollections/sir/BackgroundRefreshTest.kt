package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import com.cascadiacollections.sir.core.persistence.SavedStationRefresh
import com.cascadiacollections.sir.core.persistence.SavedStationRefreshStore
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One background refresh pass, against fakes (no WorkManager, no DataStore). */
class BackgroundRefreshTest {

    internal class FakeDirectory : RadioDirectory {
        var top: Result<List<Station>> = Result.success(listOf(station(UUID_A)))
        var tags: Result<List<Tag>> = Result.success(listOf(Tag("jazz", 1)))
        var lookup: Result<List<Station>>? = null
        val topCalls = mutableListOf<Pair<Int, Boolean>>()
        val tagCalls = mutableListOf<Pair<Int, Boolean>>()
        val lookups = mutableListOf<List<String>>()
        val clicks = mutableListOf<String>()

        override suspend fun search(query: StationQuery) = Result.success(emptyList<Station>())
        override suspend fun topStations(limit: Int) = topStations(limit, false)
        override suspend fun topStations(limit: Int, forceRefresh: Boolean): Result<List<Station>> {
            topCalls += limit to forceRefresh
            return top
        }
        override suspend fun stationsByTag(tag: String, limit: Int) = Result.success(emptyList<Station>())
        override suspend fun getStation(id: String) = Result.success<Station?>(null)
        override suspend fun getStations(ids: List<String>): Result<List<Station>> {
            lookups += ids
            return lookup ?: Result.success(ids.map { fresh(it) })
        }
        override suspend fun topTags(limit: Int) = topTags(limit, false)
        override suspend fun topTags(limit: Int, forceRefresh: Boolean): Result<List<Tag>> {
            tagCalls += limit to forceRefresh
            return tags
        }
        override suspend fun reportClick(stationId: String): Result<Unit> {
            clicks += stationId
            return Result.success(Unit)
        }
    }

    internal class FakeFavorites(stations: List<Station>) : SavedStationRefreshStore {
        override val savedStations = MutableStateFlow(stations)
        override suspend fun refreshSavedStations(fetched: List<Station>): Int {
            val result = SavedStationRefresh.merge(savedStations.value, fetched)
            savedStations.value = result.stations
            return result.updated
        }
    }

    private val directory = FakeDirectory()

    @Test
    fun `refreshes discovery past every cache`() = runTest {
        BackgroundRefresh(directory, FakeFavorites(emptyList())).run()

        assertEquals(listOf(SearchViewModel.POPULAR_LIMIT to true), directory.topCalls)
        assertEquals(listOf(RadioDirectory.DEFAULT_TAG_LIMIT to true), directory.tagCalls)
    }

    @Test
    fun `refreshes saved radio-browser stations in one lookup and never reports clicks`() = runTest {
        val favorites = FakeFavorites(
            listOf(station(UUID_A), Station(id = "imported:x", name = "X", url = "https://x.example"), station(UUID_B))
        )

        val outcome = BackgroundRefresh(directory, favorites).run()

        assertEquals(listOf(listOf(UUID_A, UUID_B)), directory.lookups)
        assertEquals(2, outcome.favoritesUpdated?.getOrThrow())
        assertEquals("https://fresh.example/$UUID_A", favorites.savedStations.value.first().urlResolved)
        assertEquals("My $UUID_A", favorites.savedStations.value.first().name)
        assertTrue(directory.clicks.isEmpty())
        assertFalse(outcome.shouldRetry)
    }

    @Test
    fun `no saved radio-browser stations makes no lookup`() = runTest {
        val outcome = BackgroundRefresh(
            directory,
            FakeFavorites(listOf(Station(id = "sir-default", url = "https://s")))
        ).run()

        assertTrue(directory.lookups.isEmpty())
        assertNull(outcome.favoritesUpdated)
    }

    @Test
    fun `a failed lookup leaves saved stations alone`() = runTest {
        directory.lookup = Result.failure(IOException("offline"))
        val saved = listOf(station(UUID_A))
        val favorites = FakeFavorites(saved)

        val outcome = BackgroundRefresh(directory, favorites).run()

        assertTrue(outcome.favoritesUpdated!!.isFailure)
        assertEquals(saved, favorites.savedStations.value)
        assertFalse("discovery succeeded", outcome.shouldRetry)
    }

    @Test
    fun `retries only when nothing attempted succeeded`() = runTest {
        directory.top = Result.failure(IOException("offline"))
        directory.tags = Result.failure(IOException("offline"))
        directory.lookup = Result.failure(IOException("offline"))

        assertTrue(BackgroundRefresh(directory, FakeFavorites(listOf(station(UUID_A)))).run().shouldRetry)
        assertTrue(BackgroundRefresh(directory, FakeFavorites(emptyList())).run().shouldRetry)
    }

    internal companion object {
        const val UUID_A = "96062a7b-0601-11e8-ae97-52543be04c81"
        const val UUID_B = "960e57c5-0601-11e8-ae97-52543be04c81"

        fun station(id: String) = Station(
            id = id,
            name = "My $id",
            url = "https://s.example/$id",
            urlResolved = "https://stale.example/$id"
        )

        fun fresh(id: String) = Station(
            id = id,
            name = "Directory $id",
            url = "https://s.example/$id",
            urlResolved = "https://fresh.example/$id",
            bitrate = 128,
            codec = "AAC"
        )
    }
}
