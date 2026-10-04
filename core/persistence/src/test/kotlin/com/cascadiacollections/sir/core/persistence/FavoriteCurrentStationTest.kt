package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.FavoriteCurrentStation.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteCurrentStationTest {

    private fun station(id: String, url: String = "https://example.com/$id") =
        Station(id = id, name = "Station $id", url = url)

    private val a = station("a")
    private val b = station("b")

    @Test
    fun `a station not yet saved is added to the end`() {
        val outcome = FavoriteCurrentStation.decide(selected = b, saved = listOf(a))

        assertEquals(Outcome.Added(b), outcome)
        assertEquals(listOf(a, b), FavoriteCurrentStation.apply(listOf(a), outcome))
    }

    @Test
    fun `an already saved station is reported and left alone`() {
        val saved = listOf(a, b)
        val outcome = FavoriteCurrentStation.decide(selected = a, saved = saved)

        assertEquals(Outcome.AlreadySaved(a), outcome)
        assertSame(saved, FavoriteCurrentStation.apply(saved, outcome))
    }

    @Test
    fun `saved is matched by id, not by equality`() {
        val edited = a.copy(name = "Renamed")

        assertEquals(Outcome.AlreadySaved(a), FavoriteCurrentStation.decide(selected = a, saved = listOf(edited)))
    }

    @Test
    fun `toggling a saved station removes it`() {
        val outcome = FavoriteCurrentStation.decide(selected = a, saved = listOf(a, b), toggle = true)

        assertEquals(Outcome.Removed(a), outcome)
        assertEquals(listOf(b), FavoriteCurrentStation.apply(listOf(a, b), outcome))
    }

    @Test
    fun `toggling an unsaved station adds it`() {
        assertEquals(Outcome.Added(a), FavoriteCurrentStation.decide(selected = a, saved = emptyList(), toggle = true))
    }

    @Test
    fun `the default stream cannot be saved`() {
        val outcome = FavoriteCurrentStation.decide(selected = null, saved = listOf(a))

        assertEquals(Outcome.DefaultStream, outcome)
        assertEquals(listOf(a), FavoriteCurrentStation.apply(listOf(a), outcome))
        assertEquals(Outcome.DefaultStream, FavoriteCurrentStation.decide(selected = null, saved = emptyList(), toggle = true))
    }

    @Test
    fun `a selection without an id or a stream is nothing selected`() {
        assertEquals(Outcome.NothingSelected, FavoriteCurrentStation.decide(station(""), emptyList()))
        assertEquals(Outcome.NothingSelected, FavoriteCurrentStation.decide(station("  "), emptyList()))
        assertEquals(Outcome.NothingSelected, FavoriteCurrentStation.decide(station("c", url = ""), emptyList()))
        assertEquals(listOf(a), FavoriteCurrentStation.apply(listOf(a), Outcome.NothingSelected))
    }

    @Test
    fun `isSaved reflects the heart state`() {
        assertTrue(FavoriteCurrentStation.isSaved(a, listOf(a)))
        assertFalse(FavoriteCurrentStation.isSaved(b, listOf(a)))
        assertFalse(FavoriteCurrentStation.isSaved(null, listOf(a)))
        assertFalse(FavoriteCurrentStation.isSaved(station(""), listOf(station(""))))
    }

    @Test
    fun `isFavoritable only for a station with an id and a stream`() {
        assertTrue(FavoriteCurrentStation.isFavoritable(a))
        assertFalse(FavoriteCurrentStation.isFavoritable(null))
        assertFalse(FavoriteCurrentStation.isFavoritable(station("")))
        assertFalse(FavoriteCurrentStation.isFavoritable(station("c", url = "")))
    }
}
