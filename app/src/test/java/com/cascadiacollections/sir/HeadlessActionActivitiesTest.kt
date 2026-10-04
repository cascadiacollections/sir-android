package com.cascadiacollections.sir

import android.os.Looper
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

        assertThat(awaitToast()).isEqualTo("Added KEXP to My Stations")
        assertThat(activity.isFinishing).isTrue()
        assertThat(runBlocking { repo().savedStations.first() }.map { it.id })
            .containsExactly("kexp")
    }

    @Test
    fun `favorite shortcut on a saved station says so and keeps it saved`() {
        runBlocking {
            repo().saveStation(station)
            repo().selectStation(station)
        }

        Robolectric.buildActivity(FavoriteCurrentStationActivity::class.java).setup()

        assertThat(awaitToast()).isEqualTo("KEXP is already in My Stations")
        assertThat(runBlocking { repo().savedStations.first() }.map { it.id })
            .containsExactly("kexp")
    }

    @Test
    fun `favorite shortcut on the default stream saves nothing`() {
        Robolectric.buildActivity(FavoriteCurrentStationActivity::class.java).setup()

        assertThat(awaitToast()).isEqualTo("SIR is always available and can't be added to My Stations")
        assertThat(runBlocking { repo().savedStations.first() }).isEmpty()
    }

    @Test
    fun `whats playing without the service says nothing is playing and never starts it`() {
        // Static, and another test class may have left a service "running" in this sandbox.
        RadioPlaybackService.isRunning = false

        Robolectric.buildActivity(NowPlayingAnnounceActivity::class.java).setup()

        assertThat(awaitToast()).isEqualTo("Nothing is playing")
        assertThat(shadowOf(app).nextStartedService).isNull()
    }
}
