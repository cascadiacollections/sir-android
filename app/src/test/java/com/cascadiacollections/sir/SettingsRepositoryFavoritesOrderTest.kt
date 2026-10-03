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

/** Reordering and merge-importing favourites through the real DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryFavoritesOrderTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    private fun station(id: String) = Station(id = id, name = "Station $id", url = "https://example.com/$id")

    @Before
    fun seed() = runBlocking {
        val repo = repo()
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
        listOf("a", "b", "c").forEach { repo.saveStation(station(it)) }
    }

    private suspend fun ids() = repo().savedStations.first().map { it.id }

    @Test
    fun `moving a saved station persists the new order`() = runBlocking {
        repo().moveSavedStation(from = 2, to = 0)
        assertEquals(listOf("c", "a", "b"), ids())
    }

    @Test
    fun `an out of range move is ignored`() = runBlocking {
        repo().moveSavedStation(from = 5, to = 0)
        assertEquals(listOf("a", "b", "c"), ids())
    }

    @Test
    fun `reordering by id persists the dragged order`() = runBlocking {
        repo().reorderSavedStations(listOf("b", "c", "a"))
        assertEquals(listOf("b", "c", "a"), ids())
    }

    @Test
    fun `new favourites still append after a reorder`() = runBlocking {
        val repo = repo()
        repo.reorderSavedStations(listOf("c", "b", "a"))
        repo.saveStation(station("d"))
        assertEquals(listOf("c", "b", "a", "d"), ids())
    }

    @Test
    fun `importing merges by id and appends in one write`() = runBlocking {
        val result = repo().importSavedStations(listOf(station("b"), station("e"), station("d")))
        assertEquals(2, result.added)
        assertEquals(1, result.skipped)
        assertEquals(listOf("a", "b", "c", "e", "d"), ids())
    }
}
