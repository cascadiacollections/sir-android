package com.cascadiacollections.sir.core.persistence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.cascadiacollections.sir.core.model.Station
import org.junit.Test

class StationCollectionsOrderingTest {

    private fun station(id: String) = Station(id = id, name = id, url = "https://example.com/$id")

    private val abcd = listOf(station("a"), station("b"), station("c"), station("d"))

    @Test
    fun `moving down shifts the stations in between up`() {
        assertThat(StationCollections.moveFavorite(abcd, 0, 2).map { it.id })
            .containsExactly("b", "c", "a", "d")
    }

    @Test
    fun `moving up shifts the stations in between down`() {
        assertThat(StationCollections.moveFavorite(abcd, 3, 0).map { it.id })
            .containsExactly("d", "a", "b", "c")
    }

    @Test
    fun `out of range or no-op moves leave the list unchanged`() {
        assertThat(StationCollections.moveFavorite(abcd, -1, 2)).isEqualTo(abcd)
        assertThat(StationCollections.moveFavorite(abcd, 0, 4)).isEqualTo(abcd)
        assertThat(StationCollections.moveFavorite(abcd, 1, 1)).isEqualTo(abcd)
        assertThat(StationCollections.moveFavorite(emptyList(), 0, 0)).isEmpty()
    }

    @Test
    fun `reorder follows the given id order`() {
        val result = StationCollections.reorderFavorites(abcd, listOf("c", "a", "d", "b"))
        assertThat(result.map { it.id }).containsExactly("c", "a", "d", "b")
    }

    @Test
    fun `reorder never drops a station missing from the order and ignores unknown ids`() {
        val result = StationCollections.reorderFavorites(abcd, listOf("d", "zzz", "b", "d"))
        assertThat(result.map { it.id }).containsExactly("d", "b", "a", "c")
    }

    @Test
    fun `merge appends new stations at the end and skips saved ids`() {
        val result = StationCollections.mergeFavorites(
            listOf(station("a"), station("b")),
            listOf(station("b").copy(name = "renamed"), station("c"), station("d"), station("c"))
        )
        assertThat(result.stations.map { it.id }).containsExactly("a", "b", "c", "d")
        assertThat(result.stations[1].name).isEqualTo("b")
        assertThat(result.added).isEqualTo(2)
        assertThat(result.skipped).isEqualTo(2)
    }

    @Test
    fun `adding a favorite after a reorder still appends`() {
        val reordered = StationCollections.moveFavorite(abcd, 3, 0)
        val result = StationCollections.addFavorite(reordered, station("e"))
        assertThat(result.map { it.id }).containsExactly("d", "a", "b", "c", "e")
    }
}
