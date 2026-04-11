package com.cascadiacollections.sir

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [FakeSettingsRepository] to validate the test double
 * used across other test files.
 */
class FakeSettingsRepositoryTest {

    private fun createRepo() = FakeSettingsRepository()

    @Test
    fun `streamQuality defaults to HIGH`() = runTest {
        val repo = createRepo()
        repo.streamQuality.test {
            assertEquals(StreamQuality.HIGH, awaitItem())
        }
    }

    @Test
    fun `setStreamQuality emits updated value`() = runTest {
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
    fun `setChromecastEnabled emits true`() = runTest {
        val repo = createRepo()
        repo.chromecastEnabled.test {
            assertFalse(awaitItem())
            repo.setChromecastEnabled(true)
            assertTrue(awaitItem())
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
    fun `setSleepTimerDuration emits updated value`() = runTest {
        val repo = createRepo()
        repo.sleepTimerDuration.test {
            assertEquals(SleepTimerDuration.OFF, awaitItem())
            repo.setSleepTimerDuration(SleepTimerDuration.SIXTY)
            assertEquals(SleepTimerDuration.SIXTY, awaitItem())
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
            repo.setSleepTimerFiresAt(999L)
            assertEquals(999L, awaitItem())
        }
    }

    @Test
    fun `setSleepTimerFiresAt with 0 or negative clears to 0`() = runTest {
        val repo = createRepo()
        repo.sleepTimerFiresAt.test {
            assertEquals(0L, awaitItem())
            repo.setSleepTimerFiresAt(500L)
            assertEquals(500L, awaitItem())
            repo.setSleepTimerFiresAt(0L)
            assertEquals(0L, awaitItem())
        }
    }

    @Test
    fun `setSleepTimerFiresAt with negative clears to 0`() = runTest {
        val repo = createRepo()
        repo.setSleepTimerFiresAt(-1L)
        repo.sleepTimerFiresAt.test {
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
    fun `setEqualizerPreset emits updated preset`() = runTest {
        val repo = createRepo()
        repo.equalizerPreset.test {
            assertEquals(EqualizerPreset.NORMAL, awaitItem())
            repo.setEqualizerPreset(EqualizerPreset.TREBLE)
            assertEquals(EqualizerPreset.TREBLE, awaitItem())
        }
    }

    @Test
    fun `customStreamUrl defaults to null`() = runTest {
        val repo = createRepo()
        repo.customStreamUrl.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `setCustomStreamUrl with URL emits it`() = runTest {
        val repo = createRepo()
        repo.customStreamUrl.test {
            assertNull(awaitItem())
            repo.setCustomStreamUrl("https://example.com/stream")
            assertEquals("https://example.com/stream", awaitItem())
        }
    }

    @Test
    fun `setCustomStreamUrl with null clears it`() = runTest {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test")
        repo.customStreamUrl.test {
            assertEquals("https://example.com/test", awaitItem())
            repo.setCustomStreamUrl(null)
            assertNull(awaitItem())
        }
    }

    @Test
    fun `setCustomStreamUrl with blank clears it`() = runTest {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test")
        repo.customStreamUrl.test {
            assertEquals("https://example.com/test", awaitItem())
            repo.setCustomStreamUrl("   ")
            assertNull(awaitItem())
        }
    }
}
