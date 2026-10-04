package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotRadioDirectoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val hour = 60 * 60 * 1000L
    private var now = 100 * hour

    private class InMemoryStore(var snapshot: DiscoverySnapshot? = null) : DiscoverySnapshotStore {
        var writes = 0
        var failWrites = false
        override suspend fun read(): DiscoverySnapshot? = snapshot
        override suspend fun write(snapshot: DiscoverySnapshot) {
            if (failWrites) throw IOException("disk full")
            writes++
            this.snapshot = snapshot
        }
    }

    /** Counts what reaches the layer beneath the snapshot. */
    private class Network : RadioDirectory {
        var top: Result<List<Station>> = Result.success(listOf(station("live")))
        var tags: Result<List<Tag>> = Result.success(listOf(Tag("live", 3)))
        val topCalls = mutableListOf<Pair<Int, Boolean>>()
        val tagCalls = mutableListOf<Pair<Int, Boolean>>()
        var searches = 0
        var genreBrowses = 0
        var lookups = 0

        override suspend fun search(query: StationQuery) = error("unfiltered overload must not be used")
        override suspend fun search(query: StationQuery, filters: StationSearchFilters): Result<List<Station>> {
            searches++
            return Result.success(listOf(station("search")))
        }
        override suspend fun topStations(limit: Int) = topStations(limit, false)
        override suspend fun topStations(limit: Int, forceRefresh: Boolean): Result<List<Station>> {
            topCalls += limit to forceRefresh
            return top
        }
        override suspend fun stationsByTag(tag: String, limit: Int) = error("unfiltered overload must not be used")
        override suspend fun stationsByTag(
            tag: String,
            limit: Int,
            filters: StationSearchFilters
        ): Result<List<Station>> {
            genreBrowses++
            return Result.success(listOf(station("genre")))
        }
        override suspend fun getStation(id: String): Result<Station?> = Result.success(null)
        override suspend fun getStations(ids: List<String>): Result<List<Station>> {
            lookups++
            return Result.success(emptyList())
        }
        override suspend fun topTags(limit: Int) = topTags(limit, false)
        override suspend fun topTags(limit: Int, forceRefresh: Boolean): Result<List<Tag>> {
            tagCalls += limit to forceRefresh
            return tags
        }
    }

    private val network = Network()

    private fun TestScope.directory(store: DiscoverySnapshotStore) =
        SnapshotRadioDirectory(network, store, backgroundScope = this, clock = { now })

    private fun snapshotAt(savedAt: Long, limit: Int = 24, tagLimit: Int = 48) = DiscoverySnapshot(
        topStations = DiscoverySnapshot.StationsSection(limit, savedAt, listOf(station("saved"))),
        topTags = DiscoverySnapshot.TagsSection(tagLimit, savedAt, listOf(Tag("saved", 1)))
    )

    @Test
    fun `a fresh snapshot is served without any request`() = runTest {
        val store = InMemoryStore(snapshotAt(now - 5 * hour))
        val dir = directory(store)

        assertEquals(listOf(station("saved")), dir.topStations(24).getOrThrow())
        assertEquals(listOf(Tag("saved", 1)), dir.topTags(48).getOrThrow())
        advanceUntilIdle()

        assertTrue(network.topCalls.isEmpty())
        assertTrue(network.tagCalls.isEmpty())
        assertEquals(0, store.writes)
    }

    @Test
    fun `a stale snapshot is served at once and refreshed in the background`() = runTest {
        val store = InMemoryStore(snapshotAt(now - 7 * hour))
        val dir = directory(store)
        val updates = mutableListOf<DiscoveryUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dir.discoveryUpdates.toList(updates)
        }

        assertEquals(listOf(station("saved")), dir.topStations(24).getOrThrow())
        assertEquals(listOf(Tag("saved", 1)), dir.topTags(48).getOrThrow())
        advanceUntilIdle()

        assertEquals(listOf(24 to false), network.topCalls)
        assertEquals(listOf(48 to false), network.tagCalls)
        assertEquals(listOf(station("live")), store.snapshot?.topStations?.stations)
        assertEquals(now, store.snapshot?.topStations?.savedAtMillis)
        assertEquals(listOf(Tag("live", 3)), store.snapshot?.topTags?.tags)
        assertEquals(
            listOf(
                DiscoveryUpdate.TopStations(24, listOf(station("live"))),
                DiscoveryUpdate.TopTags(48, listOf(Tag("live", 3)))
            ),
            updates
        )
        // Now fresh: the next call needs no request.
        assertEquals(listOf(station("live")), dir.topStations(24).getOrThrow())
        advanceUntilIdle()
        assertEquals(1, network.topCalls.size)
    }

    @Test
    fun `a failed background refresh keeps the stale snapshot`() = runTest {
        val stale = snapshotAt(now - 7 * hour)
        val store = InMemoryStore(stale)
        network.top = Result.failure(IOException("offline"))
        val dir = directory(store)

        assertEquals(listOf(station("saved")), dir.topStations(24).getOrThrow())
        advanceUntilIdle()

        assertEquals(stale, store.snapshot)
        assertEquals(0, store.writes)
    }

    @Test
    fun `only one background refresh runs per section`() = runTest {
        val dir = directory(InMemoryStore(snapshotAt(now - 7 * hour)))

        repeat(3) { dir.topStations(24) }
        advanceUntilIdle()

        assertEquals(1, network.topCalls.size)
    }

    @Test
    fun `a forced refresh always goes to the network and updates the snapshot`() = runTest {
        val store = InMemoryStore(snapshotAt(now - 1 * hour))
        val dir = directory(store)

        assertEquals(listOf(station("live")), dir.topStations(24, forceRefresh = true).getOrThrow())
        assertEquals(listOf(Tag("live", 3)), dir.topTags(48, forceRefresh = true).getOrThrow())

        assertEquals(listOf(24 to true), network.topCalls)
        assertEquals(listOf(48 to true), network.tagCalls)
        assertEquals(listOf(station("live")), store.snapshot?.topStations?.stations)
        assertEquals(listOf(Tag("live", 3)), store.snapshot?.topTags?.tags)
    }

    @Test
    fun `a failed forced refresh is passed through and the snapshot kept`() = runTest {
        val original = snapshotAt(now - 1 * hour)
        val store = InMemoryStore(original)
        network.top = Result.failure(IOException("offline"))
        val dir = directory(store)

        assertTrue(dir.topStations(24, forceRefresh = true).isFailure)
        assertEquals(original, store.snapshot)
        // The kept snapshot still answers ordinary calls.
        assertEquals(listOf(station("saved")), dir.topStations(24).getOrThrow())
    }

    @Test
    fun `no snapshot goes to the network and stores the answer`() = runTest {
        val store = InMemoryStore()
        val dir = directory(store)

        assertEquals(listOf(station("live")), dir.topStations(24).getOrThrow())

        assertEquals(listOf(24 to false), network.topCalls)
        assertEquals(DiscoverySnapshot.StationsSection(24, now, listOf(station("live"))), store.snapshot?.topStations)
    }

    @Test
    fun `a failure with no snapshot is returned and nothing stored`() = runTest {
        val store = InMemoryStore()
        network.top = Result.failure(IOException("offline"))

        assertTrue(directory(store).topStations(24).isFailure)
        assertNull(store.snapshot)
    }

    @Test
    fun `an empty answer never replaces the snapshot`() = runTest {
        val store = InMemoryStore()
        network.top = Result.success(emptyList())

        assertEquals(emptyList<Station>(), directory(store).topStations(24).getOrThrow())
        assertEquals(0, store.writes)
    }

    @Test
    fun `a different limit misses the snapshot`() = runTest {
        val store = InMemoryStore(snapshotAt(now - 1 * hour, limit = 24, tagLimit = 48))
        val dir = directory(store)

        assertEquals(listOf(station("live")), dir.topStations(10).getOrThrow())
        assertEquals(listOf(Tag("live", 3)), dir.topTags(20).getOrThrow())

        assertEquals(listOf(10 to false), network.topCalls)
        assertEquals(listOf(20 to false), network.tagCalls)
        assertEquals(10, store.snapshot?.topStations?.limit)
    }

    @Test
    fun `a clock that went backwards counts as stale`() = runTest {
        val dir = directory(InMemoryStore(snapshotAt(now + 1 * hour)))

        assertEquals(listOf(station("saved")), dir.topStations(24).getOrThrow())
        advanceUntilIdle()

        assertEquals(1, network.topCalls.size)
    }

    @Test
    fun `a failed write still serves the update from memory`() = runTest {
        val store = InMemoryStore().apply { failWrites = true }
        val dir = directory(store)

        assertEquals(listOf(station("live")), dir.topStations(24).getOrThrow())
        assertEquals(listOf(station("live")), dir.topStations(24).getOrThrow())

        assertEquals(1, network.topCalls.size)
        assertNull(store.snapshot)
    }

    @Test
    fun `search, genre browse and lookups pass through and are never persisted`() = runTest {
        val store = InMemoryStore()
        val dir = directory(store)

        repeat(2) {
            dir.search(StationQuery("jazz"))
            dir.stationsByTag("jazz", 10)
            dir.getStations(listOf("a"))
        }

        assertEquals(2, network.searches)
        assertEquals(2, network.genreBrowses)
        assertEquals(2, network.lookups)
        assertEquals(0, store.writes)
    }

    @Test
    fun `a corrupt snapshot file is ignored`() = runTest {
        val file = temp.newFile("snapshot.json").apply { writeText("{not json") }
        val dir =
            directory(FileDiscoverySnapshotStore(file, ioDispatcher = UnconfinedTestDispatcher(testScheduler)))

        assertEquals(listOf(station("live")), dir.topStations(24).getOrThrow())

        assertEquals(listOf(24 to false), network.topCalls)
        // ...and replaced by a good one.
        val reread = FileDiscoverySnapshotStore(file, UnconfinedTestDispatcher(testScheduler)).read()
        assertEquals(listOf(station("live")), reread?.topStations?.stations)
    }

    @Test
    fun `a snapshot survives a new process through the file store`() = runTest {
        val file = File(temp.root, "nested/snapshot.json")
        val io = UnconfinedTestDispatcher(testScheduler)
        directory(FileDiscoverySnapshotStore(file, io)).topStations(24).getOrThrow()

        now += 1 * hour
        val coldStart = directory(FileDiscoverySnapshotStore(file, io))
        network.top = Result.failure(IOException("offline"))

        assertEquals(listOf(station("live")), coldStart.topStations(24).getOrThrow())
        runCurrent()
        assertEquals(1, network.topCalls.size)
    }

    private companion object {
        fun station(id: String) = Station(id = id, name = id, url = "https://example.com/$id")
    }
}
