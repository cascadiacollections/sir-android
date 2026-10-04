package com.cascadiacollections.sir.core.persistence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.FavoriteCurrentStation.Outcome
import org.junit.Test

class FavoriteCurrentStationTest {

    private fun station(id: String, url: String = "https://example.com/$id") =
        Station(id = id, name = "Station $id", url = url)

    private val a = station("a")
    private val b = station("b")

    @Test
    fun `a station not yet saved is added to the end`() {
        val outcome = FavoriteCurrentStation.decide(selected = b, saved = listOf(a))

        assertThat(outcome).isEqualTo(Outcome.Added(b))
        assertThat(FavoriteCurrentStation.apply(listOf(a), outcome)).containsExactly(a, b)
    }

    @Test
    fun `an already saved station is reported and left alone`() {
        val saved = listOf(a, b)
        val outcome = FavoriteCurrentStation.decide(selected = a, saved = saved)

        assertThat(outcome).isEqualTo(Outcome.AlreadySaved(a))
        assertThat(FavoriteCurrentStation.apply(saved, outcome)).isSameInstanceAs(saved)
    }

    @Test
    fun `saved is matched by id, not by equality`() {
        val edited = a.copy(name = "Renamed")

        assertThat(FavoriteCurrentStation.decide(selected = a, saved = listOf(edited)))
            .isEqualTo(Outcome.AlreadySaved(a))
    }

    @Test
    fun `toggling a saved station removes it`() {
        val outcome = FavoriteCurrentStation.decide(selected = a, saved = listOf(a, b), toggle = true)

        assertThat(outcome).isEqualTo(Outcome.Removed(a))
        assertThat(FavoriteCurrentStation.apply(listOf(a, b), outcome)).containsExactly(b)
    }

    @Test
    fun `toggling an unsaved station adds it`() {
        assertThat(FavoriteCurrentStation.decide(selected = a, saved = emptyList(), toggle = true))
            .isEqualTo(Outcome.Added(a))
    }

    @Test
    fun `the default stream cannot be saved`() {
        val outcome = FavoriteCurrentStation.decide(selected = null, saved = listOf(a))

        assertThat(outcome).isEqualTo(Outcome.DefaultStream)
        assertThat(FavoriteCurrentStation.apply(listOf(a), outcome)).containsExactly(a)
        assertThat(FavoriteCurrentStation.decide(selected = null, saved = emptyList(), toggle = true))
            .isEqualTo(Outcome.DefaultStream)
    }

    @Test
    fun `a selection without an id or a stream is nothing selected`() {
        assertThat(FavoriteCurrentStation.decide(station(""), emptyList())).isEqualTo(Outcome.NothingSelected)
        assertThat(FavoriteCurrentStation.decide(station("  "), emptyList())).isEqualTo(Outcome.NothingSelected)
        assertThat(FavoriteCurrentStation.decide(station("c", url = ""), emptyList()))
            .isEqualTo(Outcome.NothingSelected)
        assertThat(FavoriteCurrentStation.apply(listOf(a), Outcome.NothingSelected)).containsExactly(a)
    }

    @Test
    fun `isSaved reflects the heart state`() {
        assertThat(FavoriteCurrentStation.isSaved(a, listOf(a))).isTrue()
        assertThat(FavoriteCurrentStation.isSaved(b, listOf(a))).isFalse()
        assertThat(FavoriteCurrentStation.isSaved(null, listOf(a))).isFalse()
        assertThat(FavoriteCurrentStation.isSaved(station(""), listOf(station("")))).isFalse()
    }

    @Test
    fun `isFavoritable only for a station with an id and a stream`() {
        assertThat(FavoriteCurrentStation.isFavoritable(a)).isTrue()
        assertThat(FavoriteCurrentStation.isFavoritable(null)).isFalse()
        assertThat(FavoriteCurrentStation.isFavoritable(station(""))).isFalse()
        assertThat(FavoriteCurrentStation.isFavoritable(station("c", url = ""))).isFalse()
    }
}
