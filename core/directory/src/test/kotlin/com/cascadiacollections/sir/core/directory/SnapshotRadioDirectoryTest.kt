package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isNotNull
import assertk.assertions.isNull
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

        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("saved"))
        assertThat(dir.topTags(48).getOrThrow()).containsExactly(Tag("saved", 1))
        advanceUntilIdle()

        assertThat(network.topCalls).isEmpty()
        assertThat(network.tagCalls).isEmpty()
        assertThat(store.writes).isEqualTo(0)
    }

    @Test
    fun `a stale snapshot is served at once and refreshed in the background`() = runTest {
        val store = InMemoryStore(snapshotAt(now - 7 * hour))
        val dir = directory(store)
        val updates = mutableListOf<DiscoveryUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dir.discoveryUpdates.toList(updates)
        }

        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("saved"))
        assertThat(dir.topTags(48).getOrThrow()).containsExactly(Tag("saved", 1))
        advanceUntilIdle()

        assertThat(network.topCalls).containsExactly(24 to false)
        assertThat(network.tagCalls).containsExactly(48 to false)
        assertThat(store.snapshot?.topStations?.stations).isNotNull().containsExactly(station("live"))
        assertThat(store.snapshot?.topStations?.savedAtMillis).isEqualTo(now)
        assertThat(store.snapshot?.topTags?.tags).isNotNull().containsExactly(Tag("live", 3))
        assertThat(updates).containsExactly(
            DiscoveryUpdate.TopStations(24, listOf(station("live"))),
            DiscoveryUpdate.TopTags(48, listOf(Tag("live", 3)))
        )
        // Now fresh: the next call needs no request.
        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("live"))
        advanceUntilIdle()
        assertThat(network.topCalls).hasSize(1)
    }

    @Test
    fun `a failed background refresh keeps the stale snapshot`() = runTest {
        val stale = snapshotAt(now - 7 * hour)
        val store = InMemoryStore(stale)
        network.top = Result.failure(IOException("offline"))
        val dir = directory(store)

        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("saved"))
        advanceUntilIdle()

        assertThat(store.snapshot).isEqualTo(stale)
        assertThat(store.writes).isEqualTo(0)
    }

    @Test
    fun `only one background refresh runs per section`() = runTest {
        val dir = directory(InMemoryStore(snapshotAt(now - 7 * hour)))

        repeat(3) { dir.topStations(24) }
        advanceUntilIdle()

        assertThat(network.topCalls).hasSize(1)
    }

    @Test
    fun `a forced refresh always goes to the network and updates the snapshot`() = runTest {
        val store = InMemoryStore(snapshotAt(now - 1 * hour))
        val dir = directory(store)

        assertThat(dir.topStations(24, forceRefresh = true).getOrThrow()).containsExactly(station("live"))
        assertThat(dir.topTags(48, forceRefresh = true).getOrThrow()).containsExactly(Tag("live", 3))

        assertThat(network.topCalls).containsExactly(24 to true)
        assertThat(network.tagCalls).containsExactly(48 to true)
        assertThat(store.snapshot?.topStations?.stations).isNotNull().containsExactly(station("live"))
        assertThat(store.snapshot?.topTags?.tags).isNotNull().containsExactly(Tag("live", 3))
    }

    @Test
    fun `a failed forced refresh is passed through and the snapshot kept`() = runTest {
        val original = snapshotAt(now - 1 * hour)
        val store = InMemoryStore(original)
        network.top = Result.failure(IOException("offline"))
        val dir = directory(store)

        assertThat(dir.topStations(24, forceRefresh = true)).isFailure()
        assertThat(store.snapshot).isEqualTo(original)
        // The kept snapshot still answers ordinary calls.
        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("saved"))
    }

    @Test
    fun `no snapshot goes to the network and stores the answer`() = runTest {
        val store = InMemoryStore()
        val dir = directory(store)

        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("live"))

        assertThat(network.topCalls).containsExactly(24 to false)
        assertThat(store.snapshot?.topStations)
            .isEqualTo(DiscoverySnapshot.StationsSection(24, now, listOf(station("live"))))
    }

    @Test
    fun `a failure with no snapshot is returned and nothing stored`() = runTest {
        val store = InMemoryStore()
        network.top = Result.failure(IOException("offline"))

        assertThat(directory(store).topStations(24)).isFailure()
        assertThat(store.snapshot).isNull()
    }

    @Test
    fun `an empty answer never replaces the snapshot`() = runTest {
        val store = InMemoryStore()
        network.top = Result.success(emptyList())

        assertThat(directory(store).topStations(24).getOrThrow()).isEmpty()
        assertThat(store.writes).isEqualTo(0)
    }

    @Test
    fun `a different limit misses the snapshot`() = runTest {
        val store = InMemoryStore(snapshotAt(now - 1 * hour, limit = 24, tagLimit = 48))
        val dir = directory(store)

        assertThat(dir.topStations(10).getOrThrow()).containsExactly(station("live"))
        assertThat(dir.topTags(20).getOrThrow()).containsExactly(Tag("live", 3))

        assertThat(network.topCalls).containsExactly(10 to false)
        assertThat(network.tagCalls).containsExactly(20 to false)
        assertThat(store.snapshot?.topStations?.limit).isEqualTo(10)
    }

    @Test
    fun `a clock that went backwards counts as stale`() = runTest {
        val dir = directory(InMemoryStore(snapshotAt(now + 1 * hour)))

        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("saved"))
        advanceUntilIdle()

        assertThat(network.topCalls).hasSize(1)
    }

    @Test
    fun `a failed write still serves the update from memory`() = runTest {
        val store = InMemoryStore().apply { failWrites = true }
        val dir = directory(store)

        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("live"))
        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("live"))

        assertThat(network.topCalls).hasSize(1)
        assertThat(store.snapshot).isNull()
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

        assertThat(network.searches).isEqualTo(2)
        assertThat(network.genreBrowses).isEqualTo(2)
        assertThat(network.lookups).isEqualTo(2)
        assertThat(store.writes).isEqualTo(0)
    }

    @Test
    fun `a corrupt snapshot file is ignored`() = runTest {
        val file = temp.newFile("snapshot.json").apply { writeText("{not json") }
        val dir =
            directory(FileDiscoverySnapshotStore(file, ioDispatcher = UnconfinedTestDispatcher(testScheduler)))

        assertThat(dir.topStations(24).getOrThrow()).containsExactly(station("live"))

        assertThat(network.topCalls).containsExactly(24 to false)
        // ...and replaced by a good one.
        val reread = FileDiscoverySnapshotStore(file, UnconfinedTestDispatcher(testScheduler)).read()
        assertThat(reread?.topStations?.stations).isNotNull().containsExactly(station("live"))
    }

    @Test
    fun `a snapshot survives a new process through the file store`() = runTest {
        val file = File(temp.root, "nested/snapshot.json")
        val io = UnconfinedTestDispatcher(testScheduler)
        directory(FileDiscoverySnapshotStore(file, io)).topStations(24).getOrThrow()

        now += 1 * hour
        val coldStart = directory(FileDiscoverySnapshotStore(file, io))
        network.top = Result.failure(IOException("offline"))

        assertThat(coldStart.topStations(24).getOrThrow()).containsExactly(station("live"))
        runCurrent()
        assertThat(network.topCalls).hasSize(1)
    }

    private companion object {
        fun station(id: String) = Station(id = id, name = id, url = "https://example.com/$id")
    }
}
