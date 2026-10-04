package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    private fun station(id: String) = Station(id = id, name = "Station $id", url = "https://example.com/$id")

    @Test
    fun `saved stations start empty and persist additions`() = runBlocking {
        val repo = repo()
        assertThat(repo.savedStations.first()).isEmpty()

        repo.saveStation(station("a"))

        assertThat(repo.savedStations.first().map { it.id }).containsExactly("a")
    }

    @Test
    fun `re-saving a station refreshes it without duplicating`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.saveStation(station("a").copy(bitrate = 320))

        val saved = repo.savedStations.first()
        assertThat(saved).hasSize(1)
        assertThat(saved.single().bitrate).isEqualTo(320)
    }

    @Test
    fun `removing a station clears it from the saved list`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.removeStation("a")

        assertThat(repo.savedStations.first()).isEmpty()
    }

    @Test
    fun `selecting a station also records it as recently played`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))

        assertThat(repo.selectedStation.first()?.id).isEqualTo("a")
        assertThat(repo.recentStations.first().map { it.id }).containsExactly("a")
    }

    @Test
    fun `replaying a station moves it to the front of recents`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.selectStation(station("b"))
        repo.selectStation(station("a"))

        assertThat(repo.recentStations.first().map { it.id }).containsExactly("a", "b")
    }

    @Test
    fun `most played saved stations are ordered by selection count`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("rank-a"))
        repo.saveStation(station("rank-b"))
        repo.selectStation(station("rank-b"))
        repo.selectStation(station("rank-a"))
        repo.selectStation(station("rank-b"))

        assertThat(repo.mostPlayedSavedStations.first().map { it.id })
            .containsExactly("rank-b", "rank-a")
    }

    @Test
    fun `play counts do not accumulate for unsaved stations`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("never-saved"))
        repo.saveStation(station("saved"))
        repo.selectStation(station("saved"))

        assertThat(repo.mostPlayedSavedStations.first().map { it.id }).containsExactly("saved")
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

        assertThat(repo.mostPlayedSavedStations.first().map { it.id }.sorted())
            .containsExactly("a", "b")
    }

    @Test
    fun `connection prewarming is disabled by default`() = runBlocking {
        val repo = repo()

        assertThat(repo.connectionPrewarmingEnabled.first()).isFalse()
    }

    @Test
    fun `clearing the selection reverts to the default stream`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.clearSelectedStation()

        assertThat(repo.selectedStation.first()).isNull()
    }

    @Test
    fun `clearing recents leaves favorites intact`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.selectStation(station("a"))
        repo.clearRecentStations()

        assertThat(repo.recentStations.first()).isEmpty()
        assertThat(repo.savedStations.first().map { it.id }).containsExactly("a")
    }
}

private class FakeDirectory(private val result: Result<List<Station>>) : RadioDirectory {
    override suspend fun search(query: StationQuery) = result
    override suspend fun topStations(limit: Int) = result
    override suspend fun stationsByTag(tag: String, limit: Int) = result
    override suspend fun getStation(id: String) = result.map { it.firstOrNull { s -> s.id == id } }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioBrowserViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val station = Station(id = "a", name = "A", url = "https://example.com/a")

    // Registered so the rule clears it (cancelling its DataStore collectors) before
    // resetting Dispatchers.Main; a late emission after the reset failed the test
    // intermittently with an IllegalStateException from TestMainDispatcher.
    private fun viewModel(result: Result<List<Station>>) = RadioBrowserViewModel(
        FakeDirectory(result),
        SettingsRepository(RuntimeEnvironment.getApplication())
    ).also(coroutineRule::registerViewModel)

    @Test
    fun `blank query is rejected without hitting the directory`() = runBlocking {
        val vm = viewModel(Result.success(listOf(station)))

        vm.search()

        assertThat(vm.uiState.value.error).isEqualTo("Enter a search query")
        assertThat(vm.uiState.value.searchResults).isEmpty()
    }

    @Test
    fun `successful search publishes results and clears loading`() = runBlocking {
        val vm = viewModel(Result.success(listOf(station)))

        vm.updateSearchQuery("a")
        vm.search()

        val state = vm.uiState.value
        assertThat(state.searchResults.map { it.id }).containsExactly("a")
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `empty results surface a not-found message`() = runBlocking {
        val vm = viewModel(Result.success(emptyList()))

        vm.updateSearchQuery("nothing")
        vm.search()

        assertThat(vm.uiState.value.error).isEqualTo("No stations found")
    }

    @Test
    fun `directory failure surfaces its message`() = runBlocking {
        val vm = viewModel(Result.failure(IOException("offline")))

        vm.updateSearchQuery("a")
        vm.search()

        assertThat(vm.uiState.value.error).isEqualTo("offline")
    }

    @Test
    fun `unplayable stations are never selected for playback`() = runBlocking {
        val vm = viewModel(Result.success(emptyList()))

        vm.playStation(Station(id = "broken", name = "Broken"))

        assertThat(vm.uiState.value.selectedStationId).isNull()
    }
}
