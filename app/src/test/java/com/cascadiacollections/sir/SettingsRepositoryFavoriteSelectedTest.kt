package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.FavoriteCurrentStation.Outcome
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

        assertThat(repo.favoriteSelectedStation()).isEqualTo(Outcome.Added(station("a")))
        assertThat(repo.savedStations.first().map { it.id }).containsExactly("a")
    }

    @Test
    fun `an already saved station stays saved and is not duplicated`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.selectStation(station("a"))

        assertThat(repo.favoriteSelectedStation()).isEqualTo(Outcome.AlreadySaved(station("a")))
        assertThat(repo.savedStations.first().map { it.id }).containsExactly("a")
    }

    @Test
    fun `toggle unsaves an already saved station`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))
        repo.saveStation(station("b"))
        repo.selectStation(station("a"))

        assertThat(repo.favoriteSelectedStation(toggle = true)).isEqualTo(Outcome.Removed(station("a")))
        assertThat(repo.savedStations.first().map { it.id }).containsExactly("b")
    }

    @Test
    fun `the default stream is a no-op`() = runBlocking {
        val repo = repo()
        repo.saveStation(station("a"))

        assertThat(repo.favoriteSelectedStation()).isEqualTo(Outcome.DefaultStream)
        assertThat(repo.savedStations.first().map { it.id }).containsExactly("a")
    }

    @Test
    fun `a selection with no id is a no-op`() = runBlocking {
        val repo = repo()
        repo.selectStation(Station(id = "", name = "Broken", url = "https://example.com/x"))

        assertThat(repo.favoriteSelectedStation()).isEqualTo(Outcome.NothingSelected)
        assertThat(repo.savedStations.first()).isEmpty()
    }
}
