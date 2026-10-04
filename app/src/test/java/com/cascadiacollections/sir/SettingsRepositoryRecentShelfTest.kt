package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

        assertEquals(setOf("a"), repo.hiddenRecentStationIds.first())
        assertEquals(listOf("b", "a"), repo.recentStations.first().map { it.id })
    }

    @Test
    fun `removing a recent drops it and its hidden flag, keeping the rest in order`() = runBlocking {
        val repo = repo()
        listOf("a", "b", "c").forEach { repo.selectStation(station(it)) }
        repo.hideRecentStation("b")

        repo.removeRecentStation("b")

        assertEquals(listOf("c", "a"), repo.recentStations.first().map { it.id })
        assertEquals(emptySet<String>(), repo.hiddenRecentStationIds.first())
    }

    @Test
    fun `removing an unknown recent changes nothing`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))

        repo.removeRecentStation("nope")

        assertEquals(listOf("a"), repo.recentStations.first().map { it.id })
    }

    @Test
    fun `a station that was never played cannot be hidden`() = runBlocking {
        val repo = repo()
        repo.hideRecentStation("nope")

        assertEquals(emptySet<String>(), repo.hiddenRecentStationIds.first())
    }

    @Test
    fun `unhiding restores the station`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.hideRecentStation("a")

        repo.unhideRecentStation("a")

        assertEquals(emptySet<String>(), repo.hiddenRecentStationIds.first())
    }

    @Test
    fun `playing a hidden station again un-hides it`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.selectStation(station("b"))
        repo.hideRecentStation("a")
        repo.hideRecentStation("b")

        repo.selectStation(station("a"))

        assertEquals(setOf("b"), repo.hiddenRecentStationIds.first())
    }

    @Test
    fun `clearing the recents clears the hidden set`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))
        repo.hideRecentStation("a")

        repo.clearRecentStations()

        assertEquals(emptySet<String>(), repo.hiddenRecentStationIds.first())
    }
}
