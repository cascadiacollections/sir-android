package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import com.cascadiacollections.sir.core.persistence.SavedStationRefresh
import com.cascadiacollections.sir.core.persistence.SavedStationRefreshStore
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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

        assertThat(directory.topCalls).containsExactly(SearchViewModel.POPULAR_LIMIT to true)
        assertThat(directory.tagCalls).containsExactly(RadioDirectory.DEFAULT_TAG_LIMIT to true)
    }

    @Test
    fun `refreshes saved radio-browser stations in one lookup and never reports clicks`() = runTest {
        val favorites = FakeFavorites(
            listOf(station(UUID_A), Station(id = "imported:x", name = "X", url = "https://x.example"), station(UUID_B))
        )

        val outcome = BackgroundRefresh(directory, favorites).run()

        assertThat(directory.lookups).containsExactly(listOf(UUID_A, UUID_B))
        assertThat(outcome.favoritesUpdated?.getOrThrow()).isEqualTo(2)
        assertThat(favorites.savedStations.value.first().urlResolved).isEqualTo("https://fresh.example/$UUID_A")
        assertThat(favorites.savedStations.value.first().name).isEqualTo("My $UUID_A")
        assertThat(directory.clicks).isEmpty()
        assertThat(outcome.shouldRetry).isFalse()
    }

    @Test
    fun `no saved radio-browser stations makes no lookup`() = runTest {
        val outcome = BackgroundRefresh(
            directory,
            FakeFavorites(listOf(Station(id = "sir-default", url = "https://s")))
        ).run()

        assertThat(directory.lookups).isEmpty()
        assertThat(outcome.favoritesUpdated).isNull()
    }

    @Test
    fun `a failed lookup leaves saved stations alone`() = runTest {
        directory.lookup = Result.failure(IOException("offline"))
        val saved = listOf(station(UUID_A))
        val favorites = FakeFavorites(saved)

        val outcome = BackgroundRefresh(directory, favorites).run()

        assertThat(outcome.favoritesUpdated!!).isFailure()
        assertThat(favorites.savedStations.value).isEqualTo(saved)
        assertThat(outcome.shouldRetry, name = "discovery succeeded").isFalse()
    }

    @Test
    fun `retries only when nothing attempted succeeded`() = runTest {
        directory.top = Result.failure(IOException("offline"))
        directory.tags = Result.failure(IOException("offline"))
        directory.lookup = Result.failure(IOException("offline"))

        assertThat(BackgroundRefresh(directory, FakeFavorites(listOf(station(UUID_A)))).run().shouldRetry).isTrue()
        assertThat(BackgroundRefresh(directory, FakeFavorites(emptyList())).run().shouldRetry).isTrue()
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
