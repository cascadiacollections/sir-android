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
        assertThat(ids()).containsExactly("c", "a", "b")
    }

    @Test
    fun `an out of range move is ignored`() = runBlocking {
        repo().moveSavedStation(from = 5, to = 0)
        assertThat(ids()).containsExactly("a", "b", "c")
    }

    @Test
    fun `reordering by id persists the dragged order`() = runBlocking {
        repo().reorderSavedStations(listOf("b", "c", "a"))
        assertThat(ids()).containsExactly("b", "c", "a")
    }

    @Test
    fun `new favourites still append after a reorder`() = runBlocking {
        val repo = repo()
        repo.reorderSavedStations(listOf("c", "b", "a"))
        repo.saveStation(station("d"))
        assertThat(ids()).containsExactly("c", "b", "a", "d")
    }

    @Test
    fun `importing merges by id and appends in one write`() = runBlocking {
        val result = repo().importSavedStations(listOf(station("b"), station("e"), station("d")))
        assertThat(result.added).isEqualTo(2)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(ids()).containsExactly("a", "b", "c", "e", "d")
    }
}
