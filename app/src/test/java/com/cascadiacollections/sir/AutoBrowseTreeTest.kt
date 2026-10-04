package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.cascadiacollections.sir.AutoBrowseTree.Category
import com.cascadiacollections.sir.core.model.Station
import org.junit.Test

class AutoBrowseTreeTest {

    private fun station(id: String, name: String = id, url: String = "https://example.com/$id") =
        Station(id = id, name = name, url = url)

    // ---- root ----

    @Test
    fun `root lists the three categories in tab order`() {
        assertThat(AutoBrowseTree.rootCategories(childrenLimit = null))
            .containsExactly(Category.YOUR_STATIONS, Category.RECENTLY_PLAYED, Category.TOP_STATIONS)
        assertThat(AutoBrowseTree.rootCategories(childrenLimit = 4)).hasSize(3)
    }

    @Test
    fun `root honours a smaller root-children limit and ignores a non-positive one`() {
        assertThat(AutoBrowseTree.rootCategories(2)).containsExactly(Category.YOUR_STATIONS, Category.RECENTLY_PLAYED)
        assertThat(AutoBrowseTree.rootCategories(0)).hasSize(3)
        assertThat(AutoBrowseTree.rootCategories(-1)).hasSize(3)
    }

    @Test
    fun `category ids round-trip and never collide with the root or the SIR stream`() {
        Category.entries.forEach { assertThat(Category.fromId(it.id)).isEqualTo(it) }
        val ids = Category.entries.map {
            it.id
        } + AutoBrowseTree.ROOT_ID + AutoBrowseTree.SIR_STREAM_ID
        assertThat(ids.toSet()).hasSize(ids.size)
        assertThat(Category.fromId("unknown")).isNull()
        assertThat(Category.fromId(AutoBrowseTree.ROOT_ID)).isNull()
    }

    // ---- Your Stations ----

    @Test
    fun `your stations lists saved in user order then unsaved recents newest first`() {
        val saved = listOf(station("b"), station("a"))
        val recents = listOf(station("c"), station("a"), station("d"))
        assertThat(AutoBrowseTree.yourStations(saved, recents).map { it.id })
            .containsExactly("b", "a", "c", "d")
    }

    @Test
    fun `your stations caps at 25 and keeps saved stations first`() {
        val saved = (1..20).map { station("s$it") }
        val recents = (1..20).map { station("r$it") }
        val result = AutoBrowseTree.yourStations(saved, recents)
        assertThat(result).hasSize(AutoBrowseTree.YOUR_STATIONS_LIMIT)
        assertThat(result.take(20).map { it.id }).isEqualTo(saved.map { it.id })
        assertThat(result.drop(20).map { it.id }).containsExactly("r1", "r2", "r3", "r4", "r5")
    }

    @Test
    fun `your stations drops hidden recents but never a saved station`() {
        val saved = listOf(station("a"))
        val recents = listOf(station("a"), station("b"), station("c"))
        val stations = AutoBrowseTree.yourStations(saved, recents, hiddenRecentIds = setOf("a", "b"))
        assertThat(stations.map { it.id }).containsExactly("a", "c")
    }

    @Test
    fun `unplayable or id-less stations are filtered out`() {
        val saved = listOf(station("a", url = ""), station(""), station("b"))
        assertThat(AutoBrowseTree.yourStations(saved, emptyList()).map { it.id })
            .containsExactly("b")
    }

    // ---- Recently Played / Top Stations ----

    @Test
    fun `recently played keeps recency order, drops hidden and caps at the stored 25`() {
        val recents = (1..30).map { station("r$it") }
        val result = AutoBrowseTree.recentlyPlayed(recents, hiddenRecentIds = setOf("r1"))
        assertThat(AutoBrowseTree.RECENTLY_PLAYED_LIMIT).isEqualTo(25)
        assertThat(result).hasSize(AutoBrowseTree.RECENTLY_PLAYED_LIMIT)
        assertThat(result.first().id).isEqualTo("r2")
    }

    @Test
    fun `top stations are deduped and capped at 12`() {
        val top = (1..20).map { station("t$it") } + station("t1")
        val result = AutoBrowseTree.topStations(top)
        assertThat(result).hasSize(AutoBrowseTree.TOP_STATIONS_LIMIT)
        assertThat(result.map { it.id }).isEqualTo(result.map { it.id }.distinct())
    }

    // ---- search ----

    @Test
    fun `search puts matching saved stations before directory results without duplicates`() {
        val saved = listOf(station("s1", name = "Jazz FM"), station("s2", name = "Rock"))
        val remote = listOf(station("r1", name = "Jazz 24"), station("s1", name = "Jazz FM"))
        assertThat(AutoBrowseTree.searchResults("  jazz ", saved, remote).map { it.id })
            .containsExactly("s1", "r1")
    }

    @Test
    fun `blank search returns nothing and results cap at 20`() {
        assertThat(AutoBrowseTree.searchResults("  ", listOf(station("a")), listOf(station("b")))).isEmpty()
        val remote = (1..40).map { station("r$it") }
        assertThat(AutoBrowseTree.searchResults("x", emptyList(), remote)).hasSize(AutoBrowseTree.SEARCH_LIMIT)
    }

    // ---- id resolution ----

    @Test
    fun `findStation searches sources in order`() {
        val first = listOf(station("a", name = "saved copy"))
        val second = listOf(station("a", name = "top copy"), station("b"))
        assertThat(AutoBrowseTree.findStation("a", first, second)?.name).isEqualTo("saved copy")
        assertThat(AutoBrowseTree.findStation("b", first, second)?.id).isEqualTo("b")
        assertThat(AutoBrowseTree.findStation("z", first, second)).isNull()
        assertThat(AutoBrowseTree.findStation("", listOf(station("")))).isNull()
    }

    // ---- paging ----

    @Test
    fun `page slices and tolerates unpaged and out-of-range requests`() {
        val items = (0 until 10).toList()
        assertThat(AutoBrowseTree.page(items, 0, Int.MAX_VALUE)).isEqualTo(items)
        assertThat(AutoBrowseTree.page(items, 1, 4)).containsExactly(4, 5, 6, 7)
        assertThat(AutoBrowseTree.page(items, 2, 4)).containsExactly(8, 9)
        assertThat(AutoBrowseTree.page(items, 3, 4)).isEmpty()
        assertThat(AutoBrowseTree.page(items, 1, Int.MAX_VALUE)).isEmpty()
        assertThat(AutoBrowseTree.page(items, -1, 4)).isEmpty()
        assertThat(AutoBrowseTree.page(items, 0, 0)).isEmpty()
    }
}
