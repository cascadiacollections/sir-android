package com.cascadiacollections.sir

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The station identity and favourite toggle [RadioViewModel] exposes for Now Playing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioViewModelNowPlayingTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val station =
        Station(id = "np-1", name = "Now Playing FM", url = "https://example.com/np")

    @Before
    fun reset() = runBlocking {
        val repo = SettingsRepository(app)
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
        repo.clearSelectedStation()
    }

    @Test
    fun `selected station and favourite state reach the UI`() = runBlocking {
        val settings = SettingsRepository(app)
        val vm = RadioViewModel(app, settings).also { coroutineRule.registerViewModel(it) }

        settings.selectStation(station)
        waitUntil { vm.uiState.value.station == station }
        assertThat(vm.uiState.value.isFavorite).isFalse()

        vm.toggleFavorite()
        waitUntil { vm.uiState.value.isFavorite }
        assertThat(settings.savedStations.first().map { it.id }).containsExactly(station.id)

        vm.toggleFavorite()
        waitUntil { !vm.uiState.value.isFavorite }
        assertThat(settings.savedStations.first()).isEmpty()
    }

    @Test
    fun `favourite is a no-op for the app's own stream`() = runBlocking {
        val settings = SettingsRepository(app)
        val vm = RadioViewModel(app, settings).also { coroutineRule.registerViewModel(it) }

        vm.toggleFavorite()
        delay(100)
        assertThat(settings.savedStations.first()).isEmpty()
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(10_000L) {
            while (!condition()) delay(10L)
        }
    }
}
