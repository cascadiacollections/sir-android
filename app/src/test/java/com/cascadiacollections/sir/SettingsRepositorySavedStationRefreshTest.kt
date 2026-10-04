package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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

        assertThat(updated).isEqualTo(1)
        assertThat(repo().savedStations.first())
            .containsExactly(a.copy(urlResolved = "https://new.example", bitrate = 96), b)
    }

    @Test
    fun `nothing to change writes nothing`() = runBlocking {
        assertThat(repo().refreshSavedStations(listOf(b))).isEqualTo(0)
        assertThat(repo().savedStations.first()).containsExactly(a, b)
    }
}
