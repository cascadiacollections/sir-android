package com.cascadiacollections.sir

import android.app.Application
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
        assertEquals(false, vm.uiState.value.isFavorite)

        vm.toggleFavorite()
        waitUntil { vm.uiState.value.isFavorite }
        assertEquals(listOf(station.id), settings.savedStations.first().map { it.id })

        vm.toggleFavorite()
        waitUntil { !vm.uiState.value.isFavorite }
        assertEquals(emptyList<Station>(), settings.savedStations.first())
    }

    @Test
    fun `favourite is a no-op for the app's own stream`() = runBlocking {
        val settings = SettingsRepository(app)
        val vm = RadioViewModel(app, settings).also { coroutineRule.registerViewModel(it) }

        vm.toggleFavorite()
        delay(100)
        assertEquals(emptyList<Station>(), settings.savedStations.first())
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(10_000L) {
            while (!condition()) delay(10L)
        }
    }
}
