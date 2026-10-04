package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Recently Played shelf's hidden set, against a real DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryRecentShelfTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Before
    fun reset() = runBlocking {
        val repo = repo()
        repo.clearRecentStations()
        repo.clearSelectedStation()
    }

    private fun station(id: String) = Station(id = id, name = "Station $id", url = "https://example.com/$id")

    @Test
    fun `hiding keeps the station in the recents`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.selectStation(station("b"))

        repo.hideRecentStation("a")

        assertThat(repo.hiddenRecentStationIds.first()).containsOnly("a")
        assertThat(repo.recentStations.first().map { it.id }).containsExactly("b", "a")
    }

    @Test
    fun `removing a recent drops it and its hidden flag, keeping the rest in order`() = runBlocking {
        val repo = repo()
        listOf("a", "b", "c").forEach { repo.selectStation(station(it)) }
        repo.hideRecentStation("b")

        repo.removeRecentStation("b")

        assertThat(repo.recentStations.first().map { it.id }).containsExactly("c", "a")
        assertThat(repo.hiddenRecentStationIds.first()).isEmpty()
    }

    @Test
    fun `removing an unknown recent changes nothing`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))

        repo.removeRecentStation("nope")

        assertThat(repo.recentStations.first().map { it.id }).containsExactly("a")
    }

    @Test
    fun `a station that was never played cannot be hidden`() = runBlocking {
        val repo = repo()
        repo.hideRecentStation("nope")

        assertThat(repo.hiddenRecentStationIds.first()).isEmpty()
    }

    @Test
    fun `unhiding restores the station`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.hideRecentStation("a")

        repo.unhideRecentStation("a")

        assertThat(repo.hiddenRecentStationIds.first()).isEmpty()
    }

    @Test
    fun `playing a hidden station again un-hides it`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.selectStation(station("b"))
        repo.hideRecentStation("a")
        repo.hideRecentStation("b")

        repo.selectStation(station("a"))

        assertThat(repo.hiddenRecentStationIds.first()).containsOnly("b")
    }

    @Test
    fun `clearing the recents clears the hidden set`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.hideRecentStation("a")

        repo.clearRecentStations()

        assertThat(repo.hiddenRecentStationIds.first()).isEmpty()
    }
}
