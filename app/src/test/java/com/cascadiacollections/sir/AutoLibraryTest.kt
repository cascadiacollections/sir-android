package com.cascadiacollections.sir

import androidx.media3.session.MediaConstants
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.AutoBrowseTree.Category
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import kotlinx.coroutines.test.runTest
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
        assertThat(children.map { it.mediaId }).isEqualTo(Category.entries.map { it.id })
        children.forEach {
            assertThat(it.mediaMetadata.isBrowsable).isEqualTo(true)
            assertThat(it.mediaMetadata.isPlayable).isEqualTo(false)
        }
        assertThat(children[0].mediaMetadata.title.toString()).isEqualTo("Your Stations")
        assertThat(children[1].mediaMetadata.title.toString()).isEqualTo("Recently Played")
        assertThat(children[2].mediaMetadata.title.toString()).isEqualTo("Top Stations")
    }

    @Test
    fun `root extras declare content styles and search`() {
        val extras = library.rootExtras()
        assertThat(extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE))
            .isEqualTo(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        assertThat(extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
            .isEqualTo(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        assertThat(extras.getBoolean(AutoLibrary.EXTRAS_KEY_SEARCH_SUPPORTED)).isTrue()
    }

    @Test
    fun `your stations starts with the SIR stream then saved then unsaved recents`() = runTest {
        val children = library.children(Category.YOUR_STATIONS.id, null)!!
        assertThat(children.map { it.mediaId })
            .containsExactly(AutoBrowseTree.SIR_STREAM_ID, "saved-1", "recent-1")
    }

    @Test
    fun `station items carry artwork and a genre-bitrate subtitle`() = runTest {
        val item = library.children(Category.YOUR_STATIONS.id, null)!!.first {
            it.mediaId == "saved-1"
        }
        assertThat(item.mediaMetadata.artworkUri.toString()).isEqualTo("https://example.com/a.png")
        assertThat(item.mediaMetadata.subtitle.toString()).isEqualTo("Jazz · 128 kbps")
        assertThat(item.mediaMetadata.isPlayable).isEqualTo(true)
    }

    @Test
    fun `top stations come from the directory and a failure is an empty tab`() = runTest {
        directory.top = Result.success(listOf(station(uuidTop)))
        assertThat(library.children(Category.TOP_STATIONS.id, null)!!.map { it.mediaId })
            .containsExactly(uuidTop)

        directory.top = Result.failure(IOException("offline"))
        assertThat(library.children(Category.TOP_STATIONS.id, null)!!).isEmpty()

        directory.throwOnTop = true
        assertThat(library.children(Category.TOP_STATIONS.id, null)!!).isEmpty()
    }

    @Test
    fun `unknown parent is null`() = runTest {
        assertThat(library.children("nope", null)).isNull()
    }

    @Test
    fun `ids resolve from saved, recents, top stations and the directory`() = runTest {
        directory.top = Result.success(listOf(station(uuidTop)))
        directory.lookup = mapOf(uuidElsewhere to station(uuidElsewhere))

        assertThat(library.resolveStation("saved-1")?.id).isEqualTo("saved-1")
        assertThat(library.resolveStation("recent-1")?.id).isEqualTo("recent-1")
        assertThat(library.resolveStation(uuidTop)?.id).isEqualTo(uuidTop)
        assertThat(directory.lookups).isEmpty()
        assertThat(library.resolveStation(uuidElsewhere)?.id).isEqualTo(uuidElsewhere)
        assertThat(directory.lookups).containsExactly(uuidElsewhere)
    }

    @Test
    fun `non-directory ids are never looked up and structural ids are not stations`() = runTest {
        assertThat(library.resolveStation("imported:https://x")).isNull()
        assertThat(library.resolveStation(AutoBrowseTree.SIR_STREAM_ID)).isNull()
        assertThat(library.resolveStation(Category.TOP_STATIONS.id)).isNull()
        assertThat(directory.lookups).isEmpty()
    }

    @Test
    fun `item resolves every node of the tree`() = runTest {
        assertThat(library.item(AutoBrowseTree.ROOT_ID)).isNotNull()
        assertThat(library.item(AutoBrowseTree.SIR_STREAM_ID)?.mediaId).isEqualTo(AutoBrowseTree.SIR_STREAM_ID)
        assertThat(library.item(Category.RECENTLY_PLAYED.id)?.mediaId).isEqualTo(Category.RECENTLY_PLAYED.id)
        assertThat(library.item("saved-1")?.mediaId).isEqualTo("saved-1")
        assertThat(library.item("missing")).isNull()
    }

    @Test
    fun `search merges saved matches with directory results at limit 20`() = runTest {
        saved = listOf(station("saved-jazz", name = "Jazz FM"))
        directory.searchResult = Result.success(listOf(station(uuidTop, name = "Jazz 24")))
        val results = library.search(" jazz ")
        assertThat(results.map { it.mediaId }).containsExactly("saved-jazz", uuidTop)
        assertThat(directory.searches.single()).isEqualTo(StationQuery("jazz", AutoBrowseTree.SEARCH_LIMIT))
    }

    @Test
    fun `search survives a directory failure and ignores blank queries`() = runTest {
        saved = listOf(station("saved-jazz", name = "Jazz FM"))
        directory.searchResult = Result.failure(IOException("offline"))
        assertThat(library.search("jazz").map { it.mediaId }).containsExactly("saved-jazz")
        assertThat(library.search("  ")).isEmpty()
        assertThat(directory.searches.any { it.text.isBlank() }).isFalse()
    }
}
