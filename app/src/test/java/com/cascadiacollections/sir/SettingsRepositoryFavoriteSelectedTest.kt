package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.FavoriteCurrentStation.Outcome
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

/** "Favorite this station" against a real DataStore (shortcut, Assistant, notification heart). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryFavoriteSelectedTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    private fun station(id: String) = Station(id = id, name = "Station $id", url = "https://example.com/$id")

    @Before
    fun reset() = runBlocking {
        val repo = repo()
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
        repo.clearSelectedStation()
    }

    @Test
    fun `saves the selected station when it is not saved yet`() = runBlocking {
        val repo = repo()
        repo.selectStation(station("a"))

        assertEquals(Outcome.Added(station("a")), repo.favoriteSelectedStation())
        assertEquals(listOf("a"), repo.savedStations.first().map { it.id })
    }

    @Test
    fun `an already saved station stays saved and is not duplicated`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.selectStation(station("a"))

        assertEquals(Outcome.AlreadySaved(station("a")), repo.favoriteSelectedStation())
        assertEquals(listOf("a"), repo.savedStations.first().map { it.id })
    }

    @Test
    fun `toggle unsaves an already saved station`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.saveStation(station("b"))
        repo.selectStation(station("a"))

        assertEquals(Outcome.Removed(station("a")), repo.favoriteSelectedStation(toggle = true))
        assertEquals(listOf("b"), repo.savedStations.first().map { it.id })
    }

    @Test
    fun `the default stream is a no-op`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))

        assertEquals(Outcome.DefaultStream, repo.favoriteSelectedStation())
        assertEquals(listOf("a"), repo.savedStations.first().map { it.id })
    }

    @Test
    fun `a selection with no id is a no-op`() = runBlocking {
        val repo = repo()
        repo.selectStation(Station(id = "", name = "Broken", url = "https://example.com/x"))

        assertEquals(Outcome.NothingSelected, repo.favoriteSelectedStation())
        assertEquals(emptyList<Station>(), repo.savedStations.first())
    }
}
