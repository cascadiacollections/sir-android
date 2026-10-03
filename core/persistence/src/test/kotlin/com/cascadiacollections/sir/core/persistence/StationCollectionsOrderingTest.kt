package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Test

class StationCollectionsOrderingTest {

    private fun station(id: String) = Station(id = id, name = id, url = "https://example.com/$id")

    private val abcd = listOf(station("a"), station("b"), station("c"), station("d"))

    @Test
    fun `moving down shifts the stations in between up`() {
        assertEquals(listOf("b", "c", "a", "d"), StationCollections.moveFavorite(abcd, 0, 2).map { it.id })
    }

    @Test
    fun `moving up shifts the stations in between down`() {
        assertEquals(listOf("d", "a", "b", "c"), StationCollections.moveFavorite(abcd, 3, 0).map { it.id })
    }

    @Test
    fun `out of range or no-op moves leave the list unchanged`() {
        assertEquals(abcd, StationCollections.moveFavorite(abcd, -1, 2))
        assertEquals(abcd, StationCollections.moveFavorite(abcd, 0, 4))
        assertEquals(abcd, StationCollections.moveFavorite(abcd, 1, 1))
        assertEquals(emptyList<Station>(), StationCollections.moveFavorite(emptyList(), 0, 0))
    }

    @Test
    fun `reorder follows the given id order`() {
        val result = StationCollections.reorderFavorites(abcd, listOf("c", "a", "d", "b"))
        assertEquals(listOf("c", "a", "d", "b"), result.map { it.id })
    }

    @Test
    fun `reorder never drops a station missing from the order and ignores unknown ids`() {
        val result = StationCollections.reorderFavorites(abcd, listOf("d", "zzz", "b", "d"))
        assertEquals(listOf("d", "b", "a", "c"), result.map { it.id })
    }

    @Test
    fun `merge appends new stations at the end and skips saved ids`() {
        val result = StationCollections.mergeFavorites(
            listOf(station("a"), station("b")),
            listOf(station("b").copy(name = "renamed"), station("c"), station("d"), station("c"))
        )
        assertEquals(listOf("a", "b", "c", "d"), result.stations.map { it.id })
        assertEquals("b", result.stations[1].name)
        assertEquals(2, result.added)
        assertEquals(2, result.skipped)
    }

    @Test
    fun `adding a favorite after a reorder still appends`() {
        val reordered = StationCollections.moveFavorite(abcd, 3, 0)
        val result = StationCollections.addFavorite(reordered, station("e"))
        assertEquals(listOf("d", "a", "b", "c", "e"), result.map { it.id })
    }
}
