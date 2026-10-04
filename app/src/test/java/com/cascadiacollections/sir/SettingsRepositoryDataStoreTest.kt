package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.SleepTimerDuration
import com.cascadiacollections.sir.core.playback.StreamQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [SettingsRepository] DataStore read/write round-trips.
 * Uses Robolectric for a real Application context that DataStore needs.
 *
 * These deliberately use [runBlocking] and read the flow with [first] rather than
 * `runTest` + turbine: DataStore does real IO on real threads, so `runTest`'s virtual
 * clock races ahead of the write and `awaitItem()` times out whenever the machine is
 * under load. The DataStore file is also shared across tests in this class, hence the
 * [reset] in `@Before`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryDataStoreTest {

    private fun createRepo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Before
    fun reset() = runBlocking {
        val repo = createRepo()
        repo.setStreamQuality(StreamQuality.HIGH)
        repo.setChromecastEnabled(false)
        repo.setSleepTimerDuration(SleepTimerDuration.OFF)
        repo.setSleepTimerFiresAt(0L)
        repo.setEqualizerCustomBands(emptyList())
        repo.setEqualizerPreset(EqualizerPreset.NORMAL)
        repo.setCustomStreamUrl(null)
    }

    @Test
    fun `streamQuality defaults to HIGH`() = runBlocking {
        assertThat(createRepo().streamQuality.first()).isEqualTo(StreamQuality.HIGH)
    }

    @Test
    fun `setStreamQuality persists and emits updated quality`() = runBlocking {
        val repo = createRepo()
        repo.setStreamQuality(StreamQuality.LOW)
        assertThat(repo.streamQuality.first()).isEqualTo(StreamQuality.LOW)
    }

    @Test
    fun `chromecastEnabled defaults to false`() = runBlocking {
        assertThat(createRepo().chromecastEnabled.first()).isFalse()
    }

    @Test
    fun `setChromecastEnabled persists and emits true`() = runBlocking {
        val repo = createRepo()
        repo.setChromecastEnabled(true)
        assertThat(repo.chromecastEnabled.first()).isTrue()
    }

    @Test
    fun `sleepTimerDuration defaults to OFF`() = runBlocking {
        assertThat(createRepo().sleepTimerDuration.first()).isEqualTo(SleepTimerDuration.OFF)
    }

    @Test
    fun `setSleepTimerDuration persists and emits updated duration`() = runBlocking {
        val repo = createRepo()
        repo.setSleepTimerDuration(SleepTimerDuration.THIRTY)
        assertThat(repo.sleepTimerDuration.first()).isEqualTo(SleepTimerDuration.THIRTY)
    }

    @Test
    fun `sleepTimerFiresAt defaults to 0`() = runBlocking {
        assertThat(createRepo().sleepTimerFiresAt.first()).isEqualTo(0L)
    }

    @Test
    fun `setSleepTimerFiresAt with positive value persists it`() = runBlocking {
        val repo = createRepo()
        repo.setSleepTimerFiresAt(1234567890L)
        assertThat(repo.sleepTimerFiresAt.first()).isEqualTo(1234567890L)
    }

    @Test
    fun `setSleepTimerFiresAt with 0 removes the key`() = runBlocking {
        val repo = createRepo()
        repo.setSleepTimerFiresAt(9999L)
        assertThat(repo.sleepTimerFiresAt.first()).isEqualTo(9999L)
        repo.setSleepTimerFiresAt(0L)
        assertThat(repo.sleepTimerFiresAt.first()).isEqualTo(0L)
    }

    @Test
    fun `equalizerPreset defaults to NORMAL`() = runBlocking {
        assertThat(createRepo().equalizerPreset.first()).isEqualTo(EqualizerPreset.NORMAL)
    }

    @Test
    fun `setEqualizerPreset persists and emits updated preset`() = runBlocking {
        val repo = createRepo()
        repo.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
        assertThat(repo.equalizerPreset.first()).isEqualTo(EqualizerPreset.BASS_BOOST)
    }

    @Test
    fun `equalizerUseCustomBands defaults to false and equalizerCustomBands to empty`() = runBlocking {
        val repo = createRepo()
        assertThat(repo.equalizerUseCustomBands.first()).isFalse()
        assertThat(repo.equalizerCustomBands.first()).isEmpty()
    }

    @Test
    fun `setEqualizerCustomBands persists the curve and switches to custom mode`() = runBlocking {
        val repo = createRepo()
        repo.setEqualizerCustomBands(listOf(-1f, 0f, 0.5f, 1f))

        assertThat(repo.equalizerUseCustomBands.first()).isTrue()
        assertThat(repo.equalizerCustomBands.first()).containsExactly(-1f, 0f, 0.5f, 1f)
    }

    @Test
    fun `setEqualizerPreset exits custom-band mode`() = runBlocking {
        val repo = createRepo()
        repo.setEqualizerCustomBands(listOf(1f, 1f, 1f))

        repo.setEqualizerPreset(EqualizerPreset.TREBLE)

        assertThat(repo.equalizerUseCustomBands.first()).isFalse()
        assertThat(repo.equalizerPreset.first()).isEqualTo(EqualizerPreset.TREBLE)
    }

    @Test
    fun `setCustomStreamUrl with URL persists it`() = runBlocking {
        val repo = createRepo()
        assertThat(repo.customStreamUrl.first()).isNull()
        repo.setCustomStreamUrl("https://example.com/stream")
        assertThat(repo.customStreamUrl.first()).isEqualTo("https://example.com/stream")
    }

    @Test
    fun `setCustomStreamUrl with null removes key`() = runBlocking {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test")
        assertThat(repo.customStreamUrl.first()).isEqualTo("https://example.com/test")
        repo.setCustomStreamUrl(null)
        assertThat(repo.customStreamUrl.first()).isNull()
    }

    @Test
    fun `setCustomStreamUrl with blank removes key`() = runBlocking {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test2")
        assertThat(repo.customStreamUrl.first()).isEqualTo("https://example.com/test2")
        repo.setCustomStreamUrl("   ")
        assertThat(repo.customStreamUrl.first()).isNull()
    }
}
