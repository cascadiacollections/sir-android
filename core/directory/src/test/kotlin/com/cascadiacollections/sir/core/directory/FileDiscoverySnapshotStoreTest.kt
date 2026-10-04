package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileDiscoverySnapshotStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val snapshot = DiscoverySnapshot(
        topStations = DiscoverySnapshot.StationsSection(
            limit = 24,
            savedAtMillis = 1_000L,
            stations = listOf(
                Station(id = "a", name = "A", url = "https://a.example", urlResolved = "https://a.example/r", hls = 1)
            )
        ),
        topTags = DiscoverySnapshot.TagsSection(limit = 48, savedAtMillis = 2_000L, tags = listOf(Tag("jazz", 7)))
    )

    private fun store(file: File) = FileDiscoverySnapshotStore(file, UnconfinedTestDispatcher())

    @Test
    fun `round trips and creates missing directories`() = runTest {
        val file = File(temp.root, "a/b/snapshot.json")
        store(file).write(snapshot)

        assertEquals(snapshot, store(file).read())
    }

    @Test
    fun `leaves no temp file behind`() = runTest {
        val file = File(temp.root, "snapshot.json")
        store(file).write(snapshot)
        store(file).write(snapshot.copy(topTags = null))

        assertEquals(listOf("snapshot.json"), temp.root.list()!!.toList())
        assertNull(store(file).read()?.topTags)
    }

    @Test
    fun `a missing file reads as absent`() = runTest {
        assertNull(store(File(temp.root, "none.json")).read())
    }

    @Test
    fun `a corrupt file reads as absent`() = runTest {
        val file = temp.newFile(
            "snapshot.json"
        ).apply { writeText("""{"schemaVersion":1,"topStations":{"limit":"x"""") }
        assertNull(store(file).read())
    }

    @Test
    fun `another schema version reads as absent`() = runTest {
        val file = File(temp.root, "snapshot.json")
        store(file).write(snapshot.copy(schemaVersion = DiscoverySnapshot.SCHEMA_VERSION + 1))

        assertNull(store(file).read())
    }

    @Test
    fun `a directory in the way reads as absent`() = runTest {
        assertNull(store(temp.newFolder("snapshot.json")).read())
    }
}
