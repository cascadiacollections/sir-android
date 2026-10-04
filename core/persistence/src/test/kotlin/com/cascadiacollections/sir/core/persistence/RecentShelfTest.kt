package com.cascadiacollections.sir.core.persistence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import com.cascadiacollections.sir.core.model.Station
import org.junit.Test

class RecentShelfTest {

    private fun station(id: String) = Station(id = id, name = id, url = "https://example.com/$id")

    private val recents = listOf("a", "b", "c", "d", "e", "f", "g").map(::station)

    @Test
    fun `the shelf is the newest five recents`() {
        assertThat(StationCollections.recentShelf(recents, emptySet()).map { it.id })
            .containsExactly("a", "b", "c", "d", "e")
    }

    @Test
    fun `a hidden station leaves a gap that is not refilled`() {
        val shelf = StationCollections.recentShelf(recents, setOf("b"))

        assertThat(shelf.map { it.id }).containsExactly("a", "c", "d", "e")
    }

    @Test
    fun `hiding a station outside the window changes nothing`() {
        assertThat(StationCollections.recentShelf(recents, setOf("g")).map { it.id })
            .containsExactly("a", "b", "c", "d", "e")
    }

    @Test
    fun `everything hidden gives an empty shelf`() {
        assertThat(StationCollections.recentShelf(recents.take(2), setOf("a", "b"))).isEmpty()
    }

    @Test
    fun `playing a station again un-hides it`() {
        val hidden = StationCollections.hiddenAfterPlay(setOf("a", "c"), recents, station("c"))

        assertThat(hidden).containsOnly("a")
    }

    @Test
    fun `hidden ids that fell out of the recents are pruned`() {
        val hidden = StationCollections.hiddenAfterPlay(setOf("a", "gone"), recents, station("z"))

        assertThat(hidden).containsOnly("a")
    }
}
