package com.cascadiacollections.sir

import android.content.Intent
import android.net.Uri
import android.os.Looper
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
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

/** The headless shortcut, Assistant and automation activities, end to end. */
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

    private fun playLink(uri: String?, action: String = Intent.ACTION_VIEW): PlayStationActivity =
        Robolectric.buildActivity(PlayStationActivity::class.java, Intent(action, uri?.let(Uri::parse)))
            .setup()
            .get()

    @Test
    fun `a play link hands the station to the service and finishes without UI`() {
        val activity = playLink("sir://play/a5314180-7573-4b46-aafc-51ed2d5b9e71")

        assertThat(activity.isFinishing).isTrue()
        val started = shadowOf(app).nextStartedService
        assertThat(started.component?.className).isEqualTo(RadioPlaybackService::class.java.name)
        assertThat(started.action).isEqualTo(RadioPlaybackService.ACTION_PLAY_LINK)
        assertThat(started.getStringExtra(RadioPlaybackService.EXTRA_STATION_ID))
            .isEqualTo("a5314180-7573-4b46-aafc-51ed2d5b9e71")
        assertThat(ShadowToast.getTextOfLatestToast()).isNull()
    }

    @Test
    fun `a play link without a station, or the bare action, plays the selection`() {
        playLink("sir://play")
        playLink(uri = null, action = PlayStationActivity.ACTION_PLAY_STATION)

        repeat(2) {
            val started = shadowOf(app).nextStartedService
            assertThat(started.action).isEqualTo(RadioPlaybackService.ACTION_PLAY_LINK)
            assertThat(started.hasExtra(RadioPlaybackService.EXTRA_STATION_ID)).isFalse()
        }
    }

    @Test
    fun `a malformed play link says so and starts nothing`() {
        val activity = playLink("sir://play/%3Cscript%3E")

        assertThat(activity.isFinishing).isTrue()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("That SIR play link isn't valid")
        assertThat(shadowOf(app).nextStartedService).isNull()
    }
}
