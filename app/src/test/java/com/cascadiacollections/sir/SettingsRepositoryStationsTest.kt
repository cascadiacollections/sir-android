package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Round-trip tests for the station collections persisted by [SettingsRepository].
 *
 * Uses a real DataStore via Robolectric so the JSON encoding, the single-transaction
 * read-modify-write and the flows are all exercised together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryStationsTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    /**
     * The DataStore file is shared by every test in this class, so each test starts by
     * clearing the station collections. Without this the tests would only pass in a
     * particular execution order.
     */
    @Before
    fun resetStations() = runBlocking {
        val repo = repo()
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
        repo.clearRecentStations()
        repo.clearSelectedStation()
    }

    private fun station(id: String) =
        Station(id = id, name = "Station $id", url = "https://example.com/$id")

    @Test
    fun `saved stations start empty and persist additions`() = runBlocking {
        val repo = repo()
        assertEquals(emptyList<Station>(), repo.savedStations.first())

        repo.saveStation(station("a"))

        assertEquals(listOf("a"), repo.savedStations.first().map { it.id })
    }

    @Test
    fun `re-saving a station refreshes it without duplicating`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.saveStation(station("a").copy(bitrate = 320))

        val saved = repo.savedStations.first()
        assertEquals(1, saved.size)
        assertEquals(320, saved.single().bitrate)
    }

    @Test
    fun `removing a station clears it from the saved list`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.removeStation("a")

        assertEquals(emptyList<Station>(), repo.savedStations.first())
    }

    @Test
    fun `selecting a station also records it as recently played`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))

        assertEquals("a", repo.selectedStation.first()?.id)
        assertEquals(listOf("a"), repo.recentStations.first().map { it.id })
    }

    @Test
    fun `replaying a station moves it to the front of recents`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.selectStation(station("b"))
        repo.selectStation(station("a"))

        assertEquals(listOf("a", "b"), repo.recentStations.first().map { it.id })
    }

    @Test
    fun `most played saved stations are ordered by selection count`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("rank-a"))
        repo.saveStation(station("rank-b"))
        repo.selectStation(station("rank-b"))
        repo.selectStation(station("rank-a"))
        repo.selectStation(station("rank-b"))

        assertEquals(listOf("rank-b", "rank-a"), repo.mostPlayedSavedStations.first().map { it.id })
    }

    @Test
    fun `play counts do not accumulate for unsaved stations`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("never-saved"))
        repo.saveStation(station("saved"))
        repo.selectStation(station("saved"))

        assertEquals(listOf("saved"), repo.mostPlayedSavedStations.first().map { it.id })
    }

    @Test
    fun `unsaving a station drops its play count`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.saveStation(station("b"))
        repo.selectStation(station("a"))
        repo.selectStation(station("a"))
        repo.removeStation("a")
        repo.saveStation(station("a"))

        assertEquals(listOf("a", "b"), repo.mostPlayedSavedStations.first().map { it.id }.sorted())
    }

    @Test
    fun `connection prewarming is disabled by default`() = runBlocking {
        val repo = repo()

        assertEquals(false, repo.connectionPrewarmingEnabled.first())
    }

    @Test
    fun `clearing the selection reverts to the default stream`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.clearSelectedStation()

        assertNull(repo.selectedStation.first())
    }

    @Test
    fun `clearing recents leaves favorites intact`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.selectStation(station("a"))
        repo.clearRecentStations()

        assertEquals(emptyList<Station>(), repo.recentStations.first())
        assertEquals(listOf("a"), repo.savedStations.first().map { it.id })
    }
}

private class FakeDirectory(
    private val result: Result<List<Station>>
) : RadioDirectory {
    override suspend fun search(query: StationQuery) = result
    override suspend fun topStations(limit: Int) = result
    override suspend fun stationsByTag(tag: String, limit: Int) = result
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioBrowserViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val station = Station(id = "a", name = "A", url = "https://example.com/a")

    private fun viewModel(result: Result<List<Station>>) = RadioBrowserViewModel(
        FakeDirectory(result),
        SettingsRepository(RuntimeEnvironment.getApplication())
    )

    @Test
    fun `blank query is rejected without hitting the directory`() = runBlocking {
        val vm = viewModel(Result.success(listOf(station)))

        vm.search()

        assertEquals("Enter a search query", vm.uiState.value.error)
        assertEquals(emptyList<Station>(), vm.uiState.value.searchResults)
    }

    @Test
    fun `successful search publishes results and clears loading`() = runBlocking {
        val vm = viewModel(Result.success(listOf(station)))

        vm.updateSearchQuery("a")
        vm.search()

        val state = vm.uiState.value
        assertEquals(listOf("a"), state.searchResults.map { it.id })
        assertEquals(false, state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `empty results surface a not-found message`() = runBlocking {
        val vm = viewModel(Result.success(emptyList()))

        vm.updateSearchQuery("nothing")
        vm.search()

        assertEquals("No stations found", vm.uiState.value.error)
    }

    @Test
    fun `directory failure surfaces its message`() = runBlocking {
        val vm = viewModel(Result.failure(IOException("offline")))

        vm.updateSearchQuery("a")
        vm.search()

        assertEquals("offline", vm.uiState.value.error)
    }

    @Test
    fun `unplayable stations are never selected for playback`() = runBlocking {
        val vm = viewModel(Result.success(emptyList()))

        vm.playStation(Station(id = "broken", name = "Broken"))

        assertNull(vm.uiState.value.selectedStationId)
    }
}
