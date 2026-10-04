package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests [RadioBrowserViewModel.importPlaylist] and [RadioBrowserViewModel.exportPlaylist]
 * against a real [SettingsRepository], so dedup-by-URL is exercised against the same
 * saved-stations flow the UI observes.
 *
 * [importPlaylist] persists through `viewModelScope.launch`, which hands off to the
 * repository's real DataStore write on a background thread. A [CountDownLatch] is used
 * to wait for [PlaylistImportResult] rather than asserting immediately, since nothing
 * about the [TestCoroutineRule]'s dispatcher guarantees that write has landed by the
 * time [RadioBrowserViewModel.importPlaylist] returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioBrowserViewModelPlaylistTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Before
    fun resetStations() = runBlocking {
        val repo = repo()
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
    }

    private fun viewModel(repo: SettingsRepository) = RadioBrowserViewModel(
        NoopDirectory,
        repo
    ).also(coroutineRule::registerViewModel)

    private fun RadioBrowserViewModel.importAndAwait(text: String, isPls: Boolean): PlaylistImportResult {
        val latch = CountDownLatch(1)
        var result: PlaylistImportResult? = null
        importPlaylist(text = text, isPls = isPls) {
            result = it
            latch.countDown()
        }
        assertTrue("import did not complete in time", latch.await(5, TimeUnit.SECONDS))
        return result!!
    }

    /**
     * Blocks until the viewModel's `savedStations` collector (a separate coroutine
     * started in `init`) has caught up to [expectedSize], since nothing else here
     * synchronizes with that collector.
     */
    private fun RadioBrowserViewModel.awaitSavedStationsSize(expectedSize: Int) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5)
        while (uiState.value.savedStations.size != expectedSize) {
            assertTrue("saved stations never reached size $expectedSize", System.currentTimeMillis() < deadline)
            Thread.sleep(10)
        }
    }

    @Test
    fun `importing an M3U playlist saves every station it lists`() {
        val repo = repo()
        val vm = viewModel(repo)

        val result = vm.importAndAwait(
            text = """
                #EXTM3U
                #EXTINF:-1,Station A
                https://example.com/a
                #EXTINF:-1,Station B
                https://example.com/b
            """.trimIndent(),
            isPls = false
        )

        assertEquals(PlaylistImportResult.Imported(added = 2, skipped = 0), result)
        assertEquals(
            listOf("Station A", "Station B"),
            runBlocking { repo.savedStations.first() }.map { it.name }
        )
    }

    @Test
    fun `importing a PLS playlist saves every station it lists`() {
        val repo = repo()
        val vm = viewModel(repo)

        val result = vm.importAndAwait(
            text = "[playlist]\nFile1=https://example.com/a\nTitle1=Station A\n",
            isPls = true
        )

        assertEquals(PlaylistImportResult.Imported(added = 1, skipped = 0), result)
    }

    @Test
    fun `importing a station whose URL is already saved is skipped, not duplicated`() {
        val repo = repo()
        runBlocking {
            repo.saveStation(Station(id = "existing", name = "Existing", url = "https://example.com/a"))
        }
        val vm = viewModel(repo)
        vm.awaitSavedStationsSize(1)

        val result = vm.importAndAwait(text = "https://example.com/a", isPls = false)

        assertEquals(PlaylistImportResult.Imported(added = 0, skipped = 1), result)
        assertEquals(1, runBlocking { repo.savedStations.first() }.size)
    }

    @Test
    fun `importing unparsable text reports empty`() {
        val vm = viewModel(repo())

        val result = vm.importAndAwait(text = "not a playlist", isPls = true)

        assertEquals(PlaylistImportResult.Empty, result)
    }

    @Test
    fun `exporting renders the current saved stations as M3U`() {
        val repo = repo()
        runBlocking {
            repo.saveStation(Station(id = "a", name = "Station A", url = "https://example.com/a"))
        }
        val vm = viewModel(repo)
        vm.awaitSavedStationsSize(1)

        val exported = vm.exportPlaylist()

        assertEquals("#EXTM3U\n#EXTINF:-1,Station A\nhttps://example.com/a\n", exported)
    }

    private fun RadioBrowserViewModel.importStationsAndAwait(text: String, fileName: String?): PlaylistImportResult {
        val latch = CountDownLatch(1)
        var result: PlaylistImportResult? = null
        importStations(text = text, fileName = fileName) {
            result = it
            latch.countDown()
        }
        assertTrue("import did not complete in time", latch.await(5, TimeUnit.SECONDS))
        return result!!
    }

    @Test
    fun `importing a JSON backup merges by id and appends new favourites at the end`() {
        val repo = repo()
        runBlocking {
            repo.saveStation(Station(id = "b", name = "Mine", url = "https://example.com/b"))
        }
        val vm = viewModel(repo)
        vm.awaitSavedStationsSize(1)

        val result = vm.importStationsAndAwait(
            text = """{"schemaVersion":1,"favorites":[
                {"id":"c","name":"C","streamURL":"https://example.com/c","genre":"Jazz","sortIndex":1},
                {"id":"b","name":"Theirs","streamURL":"https://example.com/b2","genre":"","sortIndex":0}]}""",
            fileName = "favorites.txt"
        )

        assertEquals(PlaylistImportResult.Imported(added = 1, skipped = 1), result)
        val saved = runBlocking { repo.savedStations.first() }
        assertEquals(listOf("b", "c"), saved.map { it.id })
        assertEquals("Mine", saved[0].name)
        assertEquals("Jazz", saved[1].tags)
    }

    @Test
    fun `backup favourites without a stream url are resolved by id, unknown ones skipped`() {
        val repo = repo()
        val known = "96062a7b-0601-11e8-ae97-52543be04c81"
        val gone = "00000000-0000-0000-0000-000000000000"
        val directory = object : RadioDirectory by NoopDirectory {
            override suspend fun getStations(ids: List<String>) = Result.success(
                ids.filter { it == known }.map {
                    Station(
                        id = it,
                        name = "Directory Name",
                        url = "https://x/listen.pls",
                        urlResolved = "https://x/stream"
                    )
                }
            )
        }
        val vm = RadioBrowserViewModel(directory, repo).also(coroutineRule::registerViewModel)

        val result = vm.importStationsAndAwait(
            """{"schemaVersion":1,"favorites":[
                {"id":"$known","name":"Jazz","genre":"","sortIndex":0},
                {"id":"$gone","name":"Gone","genre":"","sortIndex":1}]}""",
            "shoutkit-favorites.json"
        )

        assertEquals(PlaylistImportResult.Imported(added = 1, skipped = 1), result)
        vm.awaitSavedStationsSize(1)
        val saved = vm.uiState.value.savedStations.single()
        assertEquals("Jazz", saved.name)
        assertEquals("https://x/stream", saved.streamUrl)
    }

    @Test
    fun `a backup with an unknown schema is reported unreadable`() {
        val vm = viewModel(repo())

        val result = vm.importStationsAndAwait("""{"schemaVersion":99,"favorites":[]}""", "x.json")

        assertEquals(PlaylistImportResult.Unreadable, result)
    }

    @Test
    fun `a playlist picked through importStations still imports as M3U or PLS`() {
        val vm = viewModel(repo())

        assertEquals(
            PlaylistImportResult.Imported(added = 1, skipped = 0),
            vm.importStationsAndAwait("[playlist]\nFile1=https://example.com/p\n", "list.pls")
        )
    }

    @Test
    fun `exported backup round-trips through import into an empty library`() {
        val repo = repo()
        runBlocking {
            repo.saveStation(Station(id = "a", name = "A", url = "https://example.com/a", tags = "rock,pop"))
            repo.saveStation(Station(id = "b", name = "B", url = "https://example.com/b"))
        }
        val vm = viewModel(repo)
        vm.awaitSavedStationsSize(2)
        val backup = vm.exportFavoritesBackup()

        runBlocking { repo.savedStations.first().forEach { repo.removeStation(it.id) } }
        vm.awaitSavedStationsSize(0)
        val result = vm.importStationsAndAwait(backup, "shoutkit-favorites.json")

        assertEquals(PlaylistImportResult.Imported(added = 2, skipped = 0), result)
        val saved = runBlocking { repo.savedStations.first() }
        assertEquals(listOf("a", "b"), saved.map { it.id })
        assertEquals("rock", saved[0].tags)
    }
}

private object NoopDirectory : RadioDirectory {
    override suspend fun search(query: StationQuery) = Result.success(emptyList<Station>())
    override suspend fun topStations(limit: Int) = Result.success(emptyList<Station>())
    override suspend fun stationsByTag(tag: String, limit: Int) = Result.success(emptyList<Station>())
    override suspend fun getStation(id: String) = Result.success<Station?>(null)
}
