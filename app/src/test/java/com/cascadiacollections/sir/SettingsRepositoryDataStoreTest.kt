package com.cascadiacollections.sir

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [SettingsRepository] DataStore read/write round-trips.
 * Uses Robolectric for a real Application context that DataStore needs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryDataStoreTest {

    private fun createRepo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun `streamQuality defaults to HIGH`() = runTest {
        val repo = createRepo()
        repo.streamQuality.test {
            assertEquals(StreamQuality.HIGH, awaitItem())
        }
    }

    @Test
    fun `setStreamQuality persists and emits updated quality`() = runTest {
        val repo = createRepo()
        repo.streamQuality.test {
            assertEquals(StreamQuality.HIGH, awaitItem())
            repo.setStreamQuality(StreamQuality.LOW)
            assertEquals(StreamQuality.LOW, awaitItem())
        }
    }

    @Test
    fun `chromecastEnabled defaults to false`() = runTest {
        val repo = createRepo()
        repo.chromecastEnabled.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `setChromecastEnabled persists and emits true`() = runTest {
        val repo = createRepo()
        repo.chromecastEnabled.test {
            assertFalse(awaitItem())
            repo.setChromecastEnabled(true)
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `sleepTimerDuration defaults to OFF`() = runTest {
        val repo = createRepo()
        repo.sleepTimerDuration.test {
            assertEquals(SleepTimerDuration.OFF, awaitItem())
        }
    }

    @Test
    fun `setSleepTimerDuration persists and emits updated duration`() = runTest {
        val repo = createRepo()
        repo.sleepTimerDuration.test {
            assertEquals(SleepTimerDuration.OFF, awaitItem())
            repo.setSleepTimerDuration(SleepTimerDuration.THIRTY)
            assertEquals(SleepTimerDuration.THIRTY, awaitItem())
        }
    }

    @Test
    fun `sleepTimerFiresAt defaults to 0`() = runTest {
        val repo = createRepo()
        repo.sleepTimerFiresAt.test {
            assertEquals(0L, awaitItem())
        }
    }

    @Test
    fun `setSleepTimerFiresAt with positive value persists it`() = runTest {
        val repo = createRepo()
        repo.sleepTimerFiresAt.test {
            assertEquals(0L, awaitItem())
            repo.setSleepTimerFiresAt(1234567890L)
            assertEquals(1234567890L, awaitItem())
        }
    }

    @Test
    fun `setSleepTimerFiresAt with 0 removes the key`() = runTest {
        val repo = createRepo()
        repo.sleepTimerFiresAt.test {
            assertEquals(0L, awaitItem())
            repo.setSleepTimerFiresAt(9999L)
            assertEquals(9999L, awaitItem())
            repo.setSleepTimerFiresAt(0L)
            assertEquals(0L, awaitItem())
        }
    }

    @Test
    fun `equalizerPreset defaults to NORMAL`() = runTest {
        val repo = createRepo()
        repo.equalizerPreset.test {
            assertEquals(EqualizerPreset.NORMAL, awaitItem())
        }
    }

    @Test
    fun `setEqualizerPreset persists and emits updated preset`() = runTest {
        val repo = createRepo()
        repo.equalizerPreset.test {
            assertEquals(EqualizerPreset.NORMAL, awaitItem())
            repo.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
            assertEquals(EqualizerPreset.BASS_BOOST, awaitItem())
        }
    }

    @Test
    fun `setCustomStreamUrl with URL persists it`() = runTest {
        val repo = createRepo()
        // Clear any prior state
        repo.setCustomStreamUrl(null)
        repo.customStreamUrl.test {
            assertNull(awaitItem())
            repo.setCustomStreamUrl("https://example.com/stream")
            assertEquals("https://example.com/stream", awaitItem())
        }
    }

    @Test
    fun `setCustomStreamUrl with null removes key`() = runTest {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test")
        repo.customStreamUrl.test {
            assertEquals("https://example.com/test", awaitItem())
            repo.setCustomStreamUrl(null)
            assertNull(awaitItem())
        }
    }

    @Test
    fun `setCustomStreamUrl with blank removes key`() = runTest {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test2")
        repo.customStreamUrl.test {
            assertEquals("https://example.com/test2", awaitItem())
            repo.setCustomStreamUrl("   ")
            assertNull(awaitItem())
        }
    }
}
