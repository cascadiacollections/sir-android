package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationPlayReporterTest {

    private val uuid = "96062a7b-0601-11e8-ae97-52543be04c81"

    private class ClickDirectory(private val result: Result<Unit> = Result.success(Unit)) : RadioDirectory {
        val clicks = mutableListOf<String>()
        override suspend fun search(query: StationQuery) = Result.success(emptyList<Station>())
        override suspend fun topStations(limit: Int) = Result.success(emptyList<Station>())
        override suspend fun stationsByTag(tag: String, limit: Int) = Result.success(emptyList<Station>())
        override suspend fun getStation(id: String) = Result.success<Station?>(null)
        override suspend fun reportClick(stationId: String): Result<Unit> {
            clicks += stationId
            return result
        }
    }

    private fun station(id: String) = Station(id = id, name = id, url = "https://example.com/$id")

    @Test
    fun `each selection of a directory station is reported`() = runTest {
        val selections = MutableSharedFlow<Station>(extraBufferCapacity = 8)
        val directory = ClickDirectory()
        val job = StationPlayReporter(selections, { true }, { directory }).start(backgroundScope)

        // Subscribed synchronously, so nothing emitted after start() is lost.
        selections.emit(station(uuid))
        selections.emit(station(uuid))
        runCurrent()

        assertEquals(listOf(uuid, uuid), directory.clicks)
        job.cancel()
    }

    @Test
    fun `nothing is reported when the setting is off`() = runTest {
        val selections = MutableSharedFlow<Station>(extraBufferCapacity = 8)
        val directory = ClickDirectory()
        StationPlayReporter(selections, { false }, { directory }).start(backgroundScope)

        selections.emit(station(uuid))
        runCurrent()

        assertTrue(directory.clicks.isEmpty())
    }

    @Test
    fun `bundled and imported stations are skipped without reading the setting`() = runTest {
        val selections = MutableSharedFlow<Station>(extraBufferCapacity = 8)
        val directory = ClickDirectory()
        var settingReads = 0
        StationPlayReporter(selections, {
            settingReads++
            true
        }, { directory }).start(backgroundScope)

        selections.emit(station("sir-default"))
        selections.emit(station("imported:https://example.com/s"))
        runCurrent()

        assertTrue(directory.clicks.isEmpty())
        assertEquals(0, settingReads)
    }

    @Test
    fun `a failing report or setting read does not stop later reports`() = runTest {
        val selections = MutableSharedFlow<Station>(extraBufferCapacity = 8)
        val directory = ClickDirectory(Result.failure(IOException("offline")))
        var first = true
        StationPlayReporter(
            selections,
            {
                if (first) {
                    first = false
                    throw IOException("datastore")
                }
                true
            },
            { directory }
        ).start(backgroundScope)

        selections.emit(station(uuid))
        runCurrent()
        selections.emit(station(uuid))
        runCurrent()
        selections.emit(station(uuid))
        runCurrent()

        assertEquals(listOf(uuid, uuid), directory.clicks)
    }
}
