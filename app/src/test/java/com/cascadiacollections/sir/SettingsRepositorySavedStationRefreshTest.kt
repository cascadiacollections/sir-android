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

/** The background refresh's write path through the real DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositorySavedStationRefreshTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    private val a =
        Station(id = "a", name = "Mine", url = "https://a.example", urlResolved = "https://old.example")
    private val b = Station(id = "b", name = "B", url = "https://b.example")

    @Before
    fun seed() = runBlocking {
        val repo = repo()
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
        repo.saveStation(a)
        repo.saveStation(b)
    }

    @Test
    fun `refreshes stream fields in place and keeps order and names`() = runBlocking {
        val updated = repo().refreshSavedStations(
            listOf(a.copy(name = "Directory", urlResolved = "https://new.example", bitrate = 96))
        )

        assertEquals(1, updated)
        assertEquals(
            listOf(a.copy(urlResolved = "https://new.example", bitrate = 96), b),
            repo().savedStations.first()
        )
    }

    @Test
    fun `nothing to change writes nothing`() = runBlocking {
        assertEquals(0, repo().refreshSavedStations(listOf(b)))
        assertEquals(listOf(a, b), repo().savedStations.first())
    }
}
