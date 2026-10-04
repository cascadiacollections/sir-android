package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FixedDirectory(private val result: Result<List<Station>>) : RadioDirectory {
    override suspend fun search(query: StationQuery) = result
    override suspend fun topStations(limit: Int) = result
    override suspend fun stationsByTag(tag: String, limit: Int) = result
    override suspend fun getStation(id: String) = result.map { it.firstOrNull { s -> s.id == id } }
}

class CuratedFallbackDirectoryTest {

    private val failing = FixedDirectory(Result.failure(IOException("offline")))

    @Test
    fun `top stations fall back to curated list on failure`() = runTest {
        val directory = CuratedFallbackDirectory(failing)

        val stations = directory.topStations(limit = 2).getOrThrow()

        assertThat(stations).containsExactly(CuratedStations.SIR, CuratedStations.ALL[1])
    }

    @Test
    fun `search falls back to curated matches on failure`() = runTest {
        val directory = CuratedFallbackDirectory(failing)

        val stations = directory.search(StationQuery("worldwide")).getOrThrow()

        assertThat(stations.map { it.name }).containsExactly("Worldwide FM")
    }

    @Test
    fun `failure is preserved when nothing curated matches`() = runTest {
        val directory = CuratedFallbackDirectory(failing)

        val result = directory.search(StationQuery("zzzz-no-such-station"))

        assertThat(result).isFailure()
        assertThat(result).isFailure().isInstanceOf<IOException>()
    }

    @Test
    fun `an injected curated list is used by search, not just top stations`() = runTest {
        val custom = Station(id = "c1", name = "Custom Jazz", url = "https://example.com/jazz")
        val directory = CuratedFallbackDirectory(failing, curated = listOf(custom))

        // Previously search filtered CuratedStations.ALL regardless of what was injected,
        // so a caller configuring the seam got its list for topStations and the built-in
        // one for search.
        assertThat(directory.search(StationQuery("jazz")).getOrThrow()).containsExactly(custom)
        assertThat(directory.topStations(limit = 5).getOrThrow()).containsExactly(custom)
    }

    @Test
    fun `an injected curated list does not leak the built-in stations`() = runTest {
        val custom = Station(id = "c1", name = "Custom Jazz", url = "https://example.com/jazz")
        val directory = CuratedFallbackDirectory(failing, curated = listOf(custom))

        // "worldwide" matches a built-in station but nothing in the injected list, so the
        // original failure has to survive rather than being answered from ALL.
        val result = directory.search(StationQuery("worldwide"))

        assertThat(result).isFailure()
    }

    @Test
    fun `empty successful response is not masked by curated stations`() = runTest {
        val directory = CuratedFallbackDirectory(FixedDirectory(Result.success(emptyList())))

        assertThat(directory.search(StationQuery("anything")).getOrThrow()).isEmpty()
    }

    @Test
    fun `successful response passes through untouched`() = runTest {
        val live = Station(id = "live", name = "Live", url = "https://example.com/live")
        val directory = CuratedFallbackDirectory(FixedDirectory(Result.success(listOf(live))))

        assertThat(directory.topStations(5).getOrThrow()).containsExactly(live)
    }
}
