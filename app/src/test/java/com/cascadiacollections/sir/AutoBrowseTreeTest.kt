package com.cascadiacollections.sir

import com.cascadiacollections.sir.AutoBrowseTree.Category
import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBrowseTreeTest {

    private fun station(id: String, name: String = id, url: String = "https://example.com/$id") =
        Station(id = id, name = name, url = url)

    // ---- root ----

    @Test
    fun `root lists the three categories in tab order`() {
        assertEquals(
            listOf(Category.YOUR_STATIONS, Category.RECENTLY_PLAYED, Category.TOP_STATIONS),
            AutoBrowseTree.rootCategories(childrenLimit = null),
        )
        assertEquals(3, AutoBrowseTree.rootCategories(childrenLimit = 4).size)
    }

    @Test
    fun `root honours a smaller root-children limit and ignores a non-positive one`() {
        assertEquals(listOf(Category.YOUR_STATIONS, Category.RECENTLY_PLAYED), AutoBrowseTree.rootCategories(2))
        assertEquals(3, AutoBrowseTree.rootCategories(0).size)
        assertEquals(3, AutoBrowseTree.rootCategories(-1).size)
    }

    @Test
    fun `category ids round-trip and never collide with the root or the SIR stream`() {
        Category.entries.forEach { assertEquals(it, Category.fromId(it.id)) }
        val ids = Category.entries.map { it.id } + AutoBrowseTree.ROOT_ID + AutoBrowseTree.SIR_STREAM_ID
        assertEquals(ids.size, ids.toSet().size)
        assertNull(Category.fromId("unknown"))
        assertNull(Category.fromId(AutoBrowseTree.ROOT_ID))
    }

    // ---- Your Stations ----

    @Test
    fun `your stations lists saved in user order then unsaved recents newest first`() {
        val saved = listOf(station("b"), station("a"))
        val recents = listOf(station("c"), station("a"), station("d"))
        assertEquals(
            listOf("b", "a", "c", "d"),
            AutoBrowseTree.yourStations(saved, recents).map { it.id },
        )
    }

    @Test
    fun `your stations caps at 25 and keeps saved stations first`() {
        val saved = (1..20).map { station("s$it") }
        val recents = (1..20).map { station("r$it") }
        val result = AutoBrowseTree.yourStations(saved, recents)
        assertEquals(AutoBrowseTree.YOUR_STATIONS_LIMIT, result.size)
        assertEquals(saved.map { it.id }, result.take(20).map { it.id })
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), result.drop(20).map { it.id })
    }

    @Test
    fun `your stations drops hidden recents but never a saved station`() {
        val saved = listOf(station("a"))
        val recents = listOf(station("a"), station("b"), station("c"))
        assertEquals(
            listOf("a", "c"),
            AutoBrowseTree.yourStations(saved, recents, hiddenRecentIds = setOf("a", "b")).map { it.id },
        )
    }

    @Test
    fun `unplayable or id-less stations are filtered out`() {
        val saved = listOf(station("a", url = ""), station(""), station("b"))
        assertEquals(listOf("b"), AutoBrowseTree.yourStations(saved, emptyList()).map { it.id })
    }

    // ---- Recently Played / Top Stations ----

    @Test
    fun `recently played keeps recency order, drops hidden and caps at 20`() {
        val recents = (1..30).map { station("r$it") }
        val result = AutoBrowseTree.recentlyPlayed(recents, hiddenRecentIds = setOf("r1"))
        assertEquals(AutoBrowseTree.RECENTLY_PLAYED_LIMIT, result.size)
        assertEquals("r2", result.first().id)
    }

    @Test
    fun `top stations are deduped and capped at 12`() {
        val top = (1..20).map { station("t$it") } + station("t1")
        val result = AutoBrowseTree.topStations(top)
        assertEquals(AutoBrowseTree.TOP_STATIONS_LIMIT, result.size)
        assertEquals(result.map { it.id }.distinct(), result.map { it.id })
    }

    // ---- search ----

    @Test
    fun `search puts matching saved stations before directory results without duplicates`() {
        val saved = listOf(station("s1", name = "Jazz FM"), station("s2", name = "Rock"))
        val remote = listOf(station("r1", name = "Jazz 24"), station("s1", name = "Jazz FM"))
        assertEquals(
            listOf("s1", "r1"),
            AutoBrowseTree.searchResults("  jazz ", saved, remote).map { it.id },
        )
    }

    @Test
    fun `blank search returns nothing and results cap at 20`() {
        assertTrue(AutoBrowseTree.searchResults("  ", listOf(station("a")), listOf(station("b"))).isEmpty())
        val remote = (1..40).map { station("r$it") }
        assertEquals(AutoBrowseTree.SEARCH_LIMIT, AutoBrowseTree.searchResults("x", emptyList(), remote).size)
    }

    // ---- id resolution ----

    @Test
    fun `findStation searches sources in order`() {
        val first = listOf(station("a", name = "saved copy"))
        val second = listOf(station("a", name = "top copy"), station("b"))
        assertEquals("saved copy", AutoBrowseTree.findStation("a", first, second)?.name)
        assertEquals("b", AutoBrowseTree.findStation("b", first, second)?.id)
        assertNull(AutoBrowseTree.findStation("z", first, second))
        assertNull(AutoBrowseTree.findStation("", listOf(station(""))))
    }

    // ---- paging ----

    @Test
    fun `page slices and tolerates unpaged and out-of-range requests`() {
        val items = (0 until 10).toList()
        assertEquals(items, AutoBrowseTree.page(items, 0, Int.MAX_VALUE))
        assertEquals(listOf(4, 5, 6, 7), AutoBrowseTree.page(items, 1, 4))
        assertEquals(listOf(8, 9), AutoBrowseTree.page(items, 2, 4))
        assertTrue(AutoBrowseTree.page(items, 3, 4).isEmpty())
        assertTrue(AutoBrowseTree.page(items, 1, Int.MAX_VALUE).isEmpty())
        assertTrue(AutoBrowseTree.page(items, -1, 4).isEmpty())
        assertTrue(AutoBrowseTree.page(items, 0, 0).isEmpty())
    }
}
