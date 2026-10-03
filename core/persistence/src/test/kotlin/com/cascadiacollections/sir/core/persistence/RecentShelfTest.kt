package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentShelfTest {

    private fun station(id: String) = Station(id = id, name = id, url = "https://example.com/$id")

    private val recents = listOf("a", "b", "c", "d", "e", "f", "g").map(::station)

    @Test
    fun `the shelf is the newest five recents`() {
        assertEquals(listOf("a", "b", "c", "d", "e"), StationCollections.recentShelf(recents, emptySet()).map { it.id })
    }

    @Test
    fun `a hidden station leaves a gap that is not refilled`() {
        val shelf = StationCollections.recentShelf(recents, setOf("b"))

        assertEquals(listOf("a", "c", "d", "e"), shelf.map { it.id })
    }

    @Test
    fun `hiding a station outside the window changes nothing`() {
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            StationCollections.recentShelf(recents, setOf("g")).map { it.id }
        )
    }

    @Test
    fun `everything hidden gives an empty shelf`() {
        assertEquals(emptyList<Station>(), StationCollections.recentShelf(recents.take(2), setOf("a", "b")))
    }

    @Test
    fun `playing a station again un-hides it`() {
        val hidden = StationCollections.hiddenAfterPlay(setOf("a", "c"), recents, station("c"))

        assertEquals(setOf("a"), hidden)
    }

    @Test
    fun `hidden ids that fell out of the recents are pruned`() {
        val hidden = StationCollections.hiddenAfterPlay(setOf("a", "gone"), recents, station("z"))

        assertEquals(setOf("a"), hidden)
    }
}
