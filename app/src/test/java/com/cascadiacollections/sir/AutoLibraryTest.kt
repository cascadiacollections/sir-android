package com.cascadiacollections.sir

import androidx.media3.session.MediaConstants
import com.cascadiacollections.sir.AutoBrowseTree.Category
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoLibraryTest {

    private class FakeDirectory : RadioDirectory {
        var top: Result<List<Station>> = Result.success(emptyList())
        var searchResult: Result<List<Station>> = Result.success(emptyList())
        var lookup: Map<String, Station> = emptyMap()
        var throwOnTop = false
        val lookups = mutableListOf<String>()
        val searches = mutableListOf<StationQuery>()

        override suspend fun search(query: StationQuery): Result<List<Station>> {
            searches += query
            return searchResult
        }
        override suspend fun topStations(limit: Int): Result<List<Station>> {
            if (throwOnTop) throw IllegalStateException("boom")
            return top
        }
        override suspend fun stationsByTag(tag: String, limit: Int) = Result.success(emptyList<Station>())
        override suspend fun getStation(id: String): Result<Station?> {
            lookups += id
            return Result.success(lookup[id])
        }
    }

    private val uuidTop = "11111111-2222-3333-4444-555555555555"
    private val uuidElsewhere = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    private fun station(id: String, name: String = id, favicon: String? = null, tags: String = "", bitrate: Int = 0) =
        Station(
            id = id,
            name = name,
            url = "https://example.com/$id",
            favicon = favicon,
            tags = tags,
            bitrate = bitrate
        )

    private var saved = listOf(
        station("saved-1", favicon = "https://example.com/a.png", tags = "jazz,smooth", bitrate = 128)
    )
    private var recents = listOf(station("recent-1"), station("saved-1"))
    private val directory = FakeDirectory()

    private val library = AutoLibrary(
        context = RuntimeEnvironment.getApplication(),
        savedStations = { saved },
        recentStations = { recents },
        hiddenRecentIds = { emptySet() },
        directory = { directory }
    )

    @Test
    fun `root children are browsable categories`() = runTest {
        val children = library.children(AutoBrowseTree.ROOT_ID, rootChildrenLimit = 4)!!
        assertEquals(Category.entries.map { it.id }, children.map { it.mediaId })
        children.forEach {
            assertEquals(true, it.mediaMetadata.isBrowsable)
            assertEquals(false, it.mediaMetadata.isPlayable)
        }
        assertEquals("Your Stations", children[0].mediaMetadata.title.toString())
        assertEquals("Recently Played", children[1].mediaMetadata.title.toString())
        assertEquals("Top Stations", children[2].mediaMetadata.title.toString())
    }

    @Test
    fun `root extras declare content styles and search`() {
        val extras = library.rootExtras()
        assertEquals(
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE)
        )
        assertEquals(
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE)
        )
        assertTrue(extras.getBoolean(AutoLibrary.EXTRAS_KEY_SEARCH_SUPPORTED))
    }

    @Test
    fun `your stations starts with the SIR stream then saved then unsaved recents`() = runTest {
        val children = library.children(Category.YOUR_STATIONS.id, null)!!
        assertEquals(
            listOf(AutoBrowseTree.SIR_STREAM_ID, "saved-1", "recent-1"),
            children.map {
                it.mediaId
            }
        )
    }

    @Test
    fun `station items carry artwork and a genre-bitrate subtitle`() = runTest {
        val item = library.children(Category.YOUR_STATIONS.id, null)!!.first {
            it.mediaId == "saved-1"
        }
        assertEquals("https://example.com/a.png", item.mediaMetadata.artworkUri.toString())
        assertEquals("Jazz · 128 kbps", item.mediaMetadata.subtitle.toString())
        assertEquals(true, item.mediaMetadata.isPlayable)
    }

    @Test
    fun `top stations come from the directory and a failure is an empty tab`() = runTest {
        directory.top = Result.success(listOf(station(uuidTop)))
        assertEquals(
            listOf(uuidTop),
            library.children(Category.TOP_STATIONS.id, null)!!.map {
                it.mediaId
            }
        )

        directory.top = Result.failure(IOException("offline"))
        assertTrue(library.children(Category.TOP_STATIONS.id, null)!!.isEmpty())

        directory.throwOnTop = true
        assertTrue(library.children(Category.TOP_STATIONS.id, null)!!.isEmpty())
    }

    @Test
    fun `unknown parent is null`() = runTest {
        assertNull(library.children("nope", null))
    }

    @Test
    fun `ids resolve from saved, recents, top stations and the directory`() = runTest {
        directory.top = Result.success(listOf(station(uuidTop)))
        directory.lookup = mapOf(uuidElsewhere to station(uuidElsewhere))

        assertEquals("saved-1", library.resolveStation("saved-1")?.id)
        assertEquals("recent-1", library.resolveStation("recent-1")?.id)
        assertEquals(uuidTop, library.resolveStation(uuidTop)?.id)
        assertTrue(directory.lookups.isEmpty())
        assertEquals(uuidElsewhere, library.resolveStation(uuidElsewhere)?.id)
        assertEquals(listOf(uuidElsewhere), directory.lookups)
    }

    @Test
    fun `non-directory ids are never looked up and structural ids are not stations`() = runTest {
        assertNull(library.resolveStation("imported:https://x"))
        assertNull(library.resolveStation(AutoBrowseTree.SIR_STREAM_ID))
        assertNull(library.resolveStation(Category.TOP_STATIONS.id))
        assertTrue(directory.lookups.isEmpty())
    }

    @Test
    fun `item resolves every node of the tree`() = runTest {
        assertNotNull(library.item(AutoBrowseTree.ROOT_ID))
        assertEquals(AutoBrowseTree.SIR_STREAM_ID, library.item(AutoBrowseTree.SIR_STREAM_ID)?.mediaId)
        assertEquals(Category.RECENTLY_PLAYED.id, library.item(Category.RECENTLY_PLAYED.id)?.mediaId)
        assertEquals("saved-1", library.item("saved-1")?.mediaId)
        assertNull(library.item("missing"))
    }

    @Test
    fun `search merges saved matches with directory results at limit 20`() = runTest {
        saved = listOf(station("saved-jazz", name = "Jazz FM"))
        directory.searchResult = Result.success(listOf(station(uuidTop, name = "Jazz 24")))
        val results = library.search(" jazz ")
        assertEquals(listOf("saved-jazz", uuidTop), results.map { it.mediaId })
        assertEquals(StationQuery("jazz", AutoBrowseTree.SEARCH_LIMIT), directory.searches.single())
    }

    @Test
    fun `search survives a directory failure and ignores blank queries`() = runTest {
        saved = listOf(station("saved-jazz", name = "Jazz FM"))
        directory.searchResult = Result.failure(IOException("offline"))
        assertEquals(listOf("saved-jazz"), library.search("jazz").map { it.mediaId })
        assertTrue(library.search("  ").isEmpty())
        assertFalse(directory.searches.any { it.text.isBlank() })
    }
}
