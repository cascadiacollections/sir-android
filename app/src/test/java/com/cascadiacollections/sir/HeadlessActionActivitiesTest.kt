package com.cascadiacollections.sir

import android.os.Looper
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** The two headless shortcut/Assistant activities, end to end. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeadlessActionActivitiesTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private fun repo() = SettingsRepository(app)
    private val station = Station(id = "kexp", name = "KEXP", url = "https://example.com/kexp")

    @Before
    fun reset() = runBlocking {
        val repo = repo()
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
        repo.clearSelectedStation()
        ShadowToast.reset()
    }

    /** DataStore does real IO off the main thread; pump the looper until the Toast lands. */
    private fun awaitToast(): String {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast()?.let { return it }
            Thread.sleep(10)
        }
        error("No toast shown")
    }

    @Test
    fun `favorite shortcut saves the selected station without opening the app`() {
        runBlocking { repo().selectStation(station) }

        val activity = Robolectric.buildActivity(FavoriteCurrentStationActivity::class.java).setup().get()

        assertEquals("Added KEXP to My Stations", awaitToast())
        assertTrue(activity.isFinishing)
        assertEquals(listOf("kexp"), runBlocking { repo().savedStations.first() }.map { it.id })
    }

    @Test
    fun `favorite shortcut on a saved station says so and keeps it saved`() {
        runBlocking {
            repo().saveStation(station)
            repo().selectStation(station)
        }

        Robolectric.buildActivity(FavoriteCurrentStationActivity::class.java).setup()

        assertEquals("KEXP is already in My Stations", awaitToast())
        assertEquals(listOf("kexp"), runBlocking { repo().savedStations.first() }.map { it.id })
    }

    @Test
    fun `favorite shortcut on the default stream saves nothing`() {
        Robolectric.buildActivity(FavoriteCurrentStationActivity::class.java).setup()

        assertEquals("SIR is always available and can't be added to My Stations", awaitToast())
        assertTrue(runBlocking { repo().savedStations.first() }.isEmpty())
    }

    @Test
    fun `whats playing without the service says nothing is playing and never starts it`() {
        // Static, and another test class may have left a service "running" in this sandbox.
        RadioPlaybackService.isRunning = false

        Robolectric.buildActivity(NowPlayingAnnounceActivity::class.java).setup()

        assertEquals("Nothing is playing", awaitToast())
        assertEquals(null, shadowOf(app).nextStartedService)
    }
}
