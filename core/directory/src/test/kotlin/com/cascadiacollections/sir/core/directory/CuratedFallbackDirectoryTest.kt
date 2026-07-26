package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FixedDirectory(private val result: Result<List<Station>>) : RadioDirectory {
    override suspend fun search(query: StationQuery) = result
    override suspend fun topStations(limit: Int) = result
    override suspend fun stationsByTag(tag: String, limit: Int) = result
}

class CuratedFallbackDirectoryTest {

    private val failing = FixedDirectory(Result.failure(IOException("offline")))

    @Test
    fun `top stations fall back to curated list on failure`() = runTest {
        val directory = CuratedFallbackDirectory(failing)

        val stations = directory.topStations(limit = 2).getOrThrow()

        assertEquals(listOf(CuratedStations.SIR, CuratedStations.ALL[1]), stations)
    }

    @Test
    fun `search falls back to curated matches on failure`() = runTest {
        val directory = CuratedFallbackDirectory(failing)

        val stations = directory.search(StationQuery("worldwide")).getOrThrow()

        assertEquals(listOf("Worldwide FM"), stations.map { it.name })
    }

    @Test
    fun `failure is preserved when nothing curated matches`() = runTest {
        val directory = CuratedFallbackDirectory(failing)

        val result = directory.search(StationQuery("zzzz-no-such-station"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `empty successful response is not masked by curated stations`() = runTest {
        val directory = CuratedFallbackDirectory(FixedDirectory(Result.success(emptyList())))

        assertEquals(emptyList<Station>(), directory.search(StationQuery("anything")).getOrThrow())
    }

    @Test
    fun `successful response passes through untouched`() = runTest {
        val live = Station(id = "live", name = "Live", url = "https://example.com/live")
        val directory = CuratedFallbackDirectory(FixedDirectory(Result.success(listOf(live))))

        assertEquals(listOf(live), directory.topStations(5).getOrThrow())
    }
}
