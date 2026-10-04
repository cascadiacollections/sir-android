package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.directory.CachingRadioDirectory
import com.cascadiacollections.sir.core.directory.CuratedFallbackDirectory
import com.cascadiacollections.sir.core.directory.DiscoverySnapshot
import com.cascadiacollections.sir.core.directory.DiscoverySnapshotStore
import com.cascadiacollections.sir.core.directory.SnapshotRadioDirectory
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import com.cascadiacollections.sir.core.persistence.RecentShelfStore
import com.cascadiacollections.sir.core.persistence.StationCollections
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The browse tab's idle state: ShoutKit's Listen Now shelf, grid, refresh and states. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelListenNowTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val network = CountingDirectory()
    private val store = FakeShelfStore()

    /** The production decorator chain over a counting fake, so cache behaviour is real. */
    private val directory: RadioDirectory = CuratedFallbackDirectory(
        CachingRadioDirectory(network, clock = { 0L }),
        curated = listOf(CURATED)
    )

    private fun viewModel(dir: RadioDirectory = directory) =
        SearchViewModel(dir, store).also(coroutineRule::registerViewModel)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(coroutineRule.testDispatcher) { block() }

    @Test
    fun `popular stations load the top 24`() = test {
        val vm = viewModel()
        runCurrent()

        assertEquals(listOf(SearchViewModel.POPULAR_LIMIT), network.topLimits)
        assertEquals(listOf(station("live")), vm.uiState.value.popularStations)
        assertFalse(vm.uiState.value.showsSavedStationsNotice)
    }

    @Test
    fun `a forced refresh bypasses the directory cache`() = test {
        val vm = viewModel()
        runCurrent()
        // A second view model over the same chain is answered from the cache.
        viewModel()
        runCurrent()
        assertEquals(1, network.topLimits.size)
        assertEquals(1, network.tagCalls)

        network.top = Result.success(listOf(station("fresh")))
        vm.refresh()
        runCurrent()

        assertEquals(2, network.topLimits.size)
        assertEquals(2, network.tagCalls)
        assertEquals(listOf(station("fresh")), vm.uiState.value.popularStations)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `a failed refresh keeps the stations shown and flags them`() = test {
        val vm = viewModel()
        runCurrent()

        network.top = Result.failure(IOException("offline"))
        network.tags = Result.failure(IOException("offline"))
        vm.refresh()
        runCurrent()

        val state = vm.uiState.value
        assertEquals(listOf(station("live")), state.popularStations)
        assertEquals(listOf(Tag("live", 1)), state.genres)
        assertTrue(state.showsSavedStationsNotice)
        assertFalse(state.showsDirectoryUnavailable)
        assertFalse(state.isRefreshing)

        // A later successful refresh clears the notice.
        network.top = Result.success(listOf(station("back")))
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.showsSavedStationsNotice)
    }

    @Test
    fun `an offline first load shows the curated stations`() = test {
        network.top = Result.failure(IOException("offline"))
        val vm = viewModel()
        runCurrent()

        assertEquals(listOf(CURATED), vm.uiState.value.popularStations)
        assertFalse(vm.uiState.value.showsDirectoryUnavailable)
    }

    @Test
    fun `nothing at all is directory unavailable, and try again reloads`() = test {
        network.top = Result.failure(IOException("offline"))
        val bare = CachingRadioDirectory(network, clock = { 0L })
        val vm = viewModel(bare)
        runCurrent()

        assertTrue(vm.uiState.value.showsDirectoryUnavailable)
        assertFalse(vm.uiState.value.showsSavedStationsNotice)

        network.top = Result.success(listOf(station("live")))
        vm.retryPopular()
        runCurrent()

        assertFalse(vm.uiState.value.showsDirectoryUnavailable)
        assertEquals(listOf(station("live")), vm.uiState.value.popularStations)
    }

    @Test
    fun `the shelf shows the newest five recents`() = test {
        store.recents.value = (1..7).map { station("r$it") }
        val vm = viewModel()
        runCurrent()

        assertEquals((1..5).map { "r$it" }, vm.uiState.value.recentShelf.map { it.id })
    }

    @Test
    fun `the popular header shows only with the shelf`() = test {
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.uiState.value.showsPopularHeader)

        store.recents.value = listOf(station("r1"))
        runCurrent()
        assertTrue(vm.uiState.value.showsPopularHeader)

        vm.hideFromRecentlyPlayed(station("r1"))
        runCurrent()
        assertTrue(vm.uiState.value.recentShelf.isEmpty())
        assertFalse(vm.uiState.value.showsPopularHeader)
    }

    @Test
    fun `hiding leaves a gap and keeps the play history`() = test {
        store.recents.value = (1..6).map { station("r$it") }
        val vm = viewModel()
        runCurrent()

        vm.hideFromRecentlyPlayed(station("r2"))
        runCurrent()

        assertEquals(listOf("r1", "r3", "r4", "r5"), vm.uiState.value.recentShelf.map { it.id })
        assertEquals(setOf("r2"), store.hidden.value)
        assertEquals(6, store.recents.value.size)
    }

    @Test
    fun `undo restores a hidden station`() = test {
        store.recents.value = listOf(station("r1"), station("r2"))
        val vm = viewModel()
        runCurrent()

        vm.hideFromRecentlyPlayed(station("r1"))
        runCurrent()
        vm.undoHideFromRecentlyPlayed(station("r1"))
        runCurrent()

        assertEquals(listOf("r1", "r2"), vm.uiState.value.recentShelf.map { it.id })
        assertTrue(store.hidden.value.isEmpty())
    }

    @Test
    fun `playing a hidden station again brings it back`() = test {
        store.recents.value = listOf(station("r1"), station("r2"))
        val vm = viewModel()
        runCurrent()
        vm.hideFromRecentlyPlayed(station("r2"))
        runCurrent()

        store.select(station("r2"))
        runCurrent()

        assertEquals(listOf("r2", "r1"), vm.uiState.value.recentShelf.map { it.id })
    }

    private class MemorySnapshotStore(var snapshot: DiscoverySnapshot?) : DiscoverySnapshotStore {
        override suspend fun read() = snapshot
        override suspend fun write(snapshot: DiscoverySnapshot) {
            this.snapshot = snapshot
        }
    }

    private fun TestScope.snapshotChain(ageMillis: Long): RadioDirectory {
        val now = 100 * HOUR
        val saved = DiscoverySnapshot(
            topStations = DiscoverySnapshot.StationsSection(SearchViewModel.POPULAR_LIMIT, now - ageMillis, listOf(station("saved"))),
            topTags = DiscoverySnapshot.TagsSection(RadioDirectory.DEFAULT_TAG_LIMIT, now - ageMillis, listOf(Tag("saved", 1)))
        )
        return CuratedFallbackDirectory(
            SnapshotRadioDirectory(
                CachingRadioDirectory(network, clock = { 0L }),
                MemorySnapshotStore(saved),
                backgroundScope = this,
                clock = { now }
            ),
            curated = listOf(CURATED)
        )
    }

    @Test
    fun `a cold start paints a fresh snapshot with no request, even offline`() = test {
        network.top = Result.failure(IOException("offline"))
        network.tags = Result.failure(IOException("offline"))

        val vm = viewModel(snapshotChain(ageMillis = 1 * HOUR))
        runCurrent()

        assertEquals(listOf(station("saved")), vm.uiState.value.popularStations)
        assertEquals(listOf(Tag("saved", 1)), vm.uiState.value.genres)
        assertTrue(network.topLimits.isEmpty())
        assertEquals(0, network.tagCalls)
    }

    @Test
    fun `a stale snapshot is shown at once and replaced when the background refresh lands`() = test {
        val vm = viewModel(snapshotChain(ageMillis = 7 * HOUR))

        // Both the stale answer and the revalidation ran (the dispatcher is unconfined).
        runCurrent()

        assertEquals(listOf(SearchViewModel.POPULAR_LIMIT), network.topLimits)
        assertEquals(listOf(station("live")), vm.uiState.value.popularStations)
        assertEquals(listOf(Tag("live", 1)), vm.uiState.value.genres)
        assertFalse(vm.uiState.value.showsSavedStationsNotice)
    }

    /** Counts what reaches "the network" beneath the cache. */
    private class CountingDirectory : RadioDirectory {
        var top: Result<List<Station>> = Result.success(listOf(station("live")))
        var tags: Result<List<Tag>> = Result.success(listOf(Tag("live", 1)))
        val topLimits = mutableListOf<Int>()
        var tagCalls = 0

        override suspend fun search(query: StationQuery) = Result.success(emptyList<Station>())
        override suspend fun topStations(limit: Int): Result<List<Station>> {
            topLimits += limit
            return top
        }
        override suspend fun stationsByTag(tag: String, limit: Int) = Result.success(emptyList<Station>())
        override suspend fun getStation(id: String) = Result.success<Station?>(null)
        override suspend fun topTags(limit: Int): Result<List<Tag>> {
            tagCalls++
            return tags
        }
    }

    /** Applies the same collection rules as SettingsRepository, in memory. */
    private class FakeShelfStore : RecentShelfStore {
        override val recentStations = MutableStateFlow<List<Station>>(emptyList())
        override val hiddenRecentStationIds = MutableStateFlow<Set<String>>(emptySet())
        val recents get() = recentStations
        val hidden get() = hiddenRecentStationIds

        override suspend fun hideRecentStation(stationId: String) {
            if (recentStations.value.any { it.id == stationId }) hiddenRecentStationIds.value += stationId
        }

        override suspend fun unhideRecentStation(stationId: String) {
            hiddenRecentStationIds.value -= stationId
        }

        fun select(station: Station) {
            val updated = StationCollections.recordRecent(recentStations.value, station)
            hiddenRecentStationIds.value = StationCollections.hiddenAfterPlay(hiddenRecentStationIds.value, updated, station)
            recentStations.value = updated
        }
    }

    private companion object {
        val CURATED = station("curated")
        const val HOUR = 60 * 60 * 1000L

        fun station(id: String) = Station(id = id, name = id, url = "https://example.com/$id")
    }
}
