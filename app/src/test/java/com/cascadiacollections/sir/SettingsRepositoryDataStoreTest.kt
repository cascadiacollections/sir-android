package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.SleepTimerDuration
import com.cascadiacollections.sir.core.playback.StreamQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        repo.setEqualizerPreset(EqualizerPreset.NORMAL)
        repo.setCustomStreamUrl(null)
    }

    @Test
    fun `streamQuality defaults to HIGH`() = runBlocking {
        assertEquals(StreamQuality.HIGH, createRepo().streamQuality.first())
    }

    @Test
    fun `setStreamQuality persists and emits updated quality`() = runBlocking {
        val repo = createRepo()
        repo.setStreamQuality(StreamQuality.LOW)
        assertEquals(StreamQuality.LOW, repo.streamQuality.first())
    }

    @Test
    fun `chromecastEnabled defaults to false`() = runBlocking {
        assertFalse(createRepo().chromecastEnabled.first())
    }

    @Test
    fun `setChromecastEnabled persists and emits true`() = runBlocking {
        val repo = createRepo()
        repo.setChromecastEnabled(true)
        assertTrue(repo.chromecastEnabled.first())
    }

    @Test
    fun `sleepTimerDuration defaults to OFF`() = runBlocking {
        assertEquals(SleepTimerDuration.OFF, createRepo().sleepTimerDuration.first())
    }

    @Test
    fun `setSleepTimerDuration persists and emits updated duration`() = runBlocking {
        val repo = createRepo()
        repo.setSleepTimerDuration(SleepTimerDuration.THIRTY)
        assertEquals(SleepTimerDuration.THIRTY, repo.sleepTimerDuration.first())
    }

    @Test
    fun `sleepTimerFiresAt defaults to 0`() = runBlocking {
        assertEquals(0L, createRepo().sleepTimerFiresAt.first())
    }

    @Test
    fun `setSleepTimerFiresAt with positive value persists it`() = runBlocking {
        val repo = createRepo()
        repo.setSleepTimerFiresAt(1234567890L)
        assertEquals(1234567890L, repo.sleepTimerFiresAt.first())
    }

    @Test
    fun `setSleepTimerFiresAt with 0 removes the key`() = runBlocking {
        val repo = createRepo()
        repo.setSleepTimerFiresAt(9999L)
        assertEquals(9999L, repo.sleepTimerFiresAt.first())
        repo.setSleepTimerFiresAt(0L)
        assertEquals(0L, repo.sleepTimerFiresAt.first())
    }

    @Test
    fun `equalizerPreset defaults to NORMAL`() = runBlocking {
        assertEquals(EqualizerPreset.NORMAL, createRepo().equalizerPreset.first())
    }

    @Test
    fun `setEqualizerPreset persists and emits updated preset`() = runBlocking {
        val repo = createRepo()
        repo.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
        assertEquals(EqualizerPreset.BASS_BOOST, repo.equalizerPreset.first())
    }

    @Test
    fun `setCustomStreamUrl with URL persists it`() = runBlocking {
        val repo = createRepo()
        assertNull(repo.customStreamUrl.first())
        repo.setCustomStreamUrl("https://example.com/stream")
        assertEquals("https://example.com/stream", repo.customStreamUrl.first())
    }

    @Test
    fun `setCustomStreamUrl with null removes key`() = runBlocking {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test")
        assertEquals("https://example.com/test", repo.customStreamUrl.first())
        repo.setCustomStreamUrl(null)
        assertNull(repo.customStreamUrl.first())
    }

    @Test
    fun `setCustomStreamUrl with blank removes key`() = runBlocking {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test2")
        assertEquals("https://example.com/test2", repo.customStreamUrl.first())
        repo.setCustomStreamUrl("   ")
        assertNull(repo.customStreamUrl.first())
    }
}
