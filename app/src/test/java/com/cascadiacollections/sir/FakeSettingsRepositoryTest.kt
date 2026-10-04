package com.cascadiacollections.sir

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.SleepTimerDuration
import com.cascadiacollections.sir.core.playback.StreamQuality
import kotlinx.coroutines.test.runTest
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
            assertThat(awaitItem()).isEqualTo(StreamQuality.HIGH)
        }
    }

    @Test
    fun `setStreamQuality emits updated value`() = runTest {
        val repo = createRepo()
        repo.streamQuality.test {
            assertThat(awaitItem()).isEqualTo(StreamQuality.HIGH)
            repo.setStreamQuality(StreamQuality.LOW)
            assertThat(awaitItem()).isEqualTo(StreamQuality.LOW)
        }
    }

    @Test
    fun `chromecastEnabled defaults to false`() = runTest {
        val repo = createRepo()
        repo.chromecastEnabled.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `setChromecastEnabled emits true`() = runTest {
        val repo = createRepo()
        repo.chromecastEnabled.test {
            assertThat(awaitItem()).isFalse()
            repo.setChromecastEnabled(true)
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `sleepTimerDuration defaults to OFF`() = runTest {
        val repo = createRepo()
        repo.sleepTimerDuration.test {
            assertThat(awaitItem()).isEqualTo(SleepTimerDuration.OFF)
        }
    }

    @Test
    fun `setSleepTimerDuration emits updated value`() = runTest {
        val repo = createRepo()
        repo.sleepTimerDuration.test {
            assertThat(awaitItem()).isEqualTo(SleepTimerDuration.OFF)
            repo.setSleepTimerDuration(SleepTimerDuration.SIXTY)
            assertThat(awaitItem()).isEqualTo(SleepTimerDuration.SIXTY)
        }
    }

    @Test
    fun `sleepTimerFiresAt defaults to 0`() = runTest {
        val repo = createRepo()
        repo.sleepTimerFiresAt.test {
            assertThat(awaitItem()).isEqualTo(0L)
        }
    }

    @Test
    fun `setSleepTimerFiresAt with positive value persists it`() = runTest {
        val repo = createRepo()
        repo.sleepTimerFiresAt.test {
            assertThat(awaitItem()).isEqualTo(0L)
            repo.setSleepTimerFiresAt(999L)
            assertThat(awaitItem()).isEqualTo(999L)
        }
    }

    @Test
    fun `setSleepTimerFiresAt with 0 or negative clears to 0`() = runTest {
        val repo = createRepo()
        repo.sleepTimerFiresAt.test {
            assertThat(awaitItem()).isEqualTo(0L)
            repo.setSleepTimerFiresAt(500L)
            assertThat(awaitItem()).isEqualTo(500L)
            repo.setSleepTimerFiresAt(0L)
            assertThat(awaitItem()).isEqualTo(0L)
        }
    }

    @Test
    fun `setSleepTimerFiresAt with negative clears to 0`() = runTest {
        val repo = createRepo()
        repo.setSleepTimerFiresAt(-1L)
        repo.sleepTimerFiresAt.test {
            assertThat(awaitItem()).isEqualTo(0L)
        }
    }

    @Test
    fun `equalizerPreset defaults to NORMAL`() = runTest {
        val repo = createRepo()
        repo.equalizerPreset.test {
            assertThat(awaitItem()).isEqualTo(EqualizerPreset.NORMAL)
        }
    }

    @Test
    fun `setEqualizerPreset emits updated preset`() = runTest {
        val repo = createRepo()
        repo.equalizerPreset.test {
            assertThat(awaitItem()).isEqualTo(EqualizerPreset.NORMAL)
            repo.setEqualizerPreset(EqualizerPreset.TREBLE)
            assertThat(awaitItem()).isEqualTo(EqualizerPreset.TREBLE)
        }
    }

    @Test
    fun `customStreamUrl defaults to null`() = runTest {
        val repo = createRepo()
        repo.customStreamUrl.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `setCustomStreamUrl with URL emits it`() = runTest {
        val repo = createRepo()
        repo.customStreamUrl.test {
            assertThat(awaitItem()).isNull()
            repo.setCustomStreamUrl("https://example.com/stream")
            assertThat(awaitItem()).isEqualTo("https://example.com/stream")
        }
    }

    @Test
    fun `setCustomStreamUrl with null clears it`() = runTest {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test")
        repo.customStreamUrl.test {
            assertThat(awaitItem()).isEqualTo("https://example.com/test")
            repo.setCustomStreamUrl(null)
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `setCustomStreamUrl with blank clears it`() = runTest {
        val repo = createRepo()
        repo.setCustomStreamUrl("https://example.com/test")
        repo.customStreamUrl.test {
            assertThat(awaitItem()).isEqualTo("https://example.com/test")
            repo.setCustomStreamUrl("   ")
            assertThat(awaitItem()).isNull()
        }
    }
}
