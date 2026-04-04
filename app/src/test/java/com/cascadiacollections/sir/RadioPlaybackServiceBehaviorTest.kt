package com.cascadiacollections.sir

import android.content.Intent
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Behavior tests for [RadioPlaybackService].
 *
 * The service creates ExoPlayer + MediaLibrarySession in onCreate() which
 * requires full Media3 infrastructure. Tests that need a running service
 * use Robolectric's ServiceController and verify observable effects
 * (notifications, foreground state). Tests for extracted logic and
 * feature-flag gating run without the full service lifecycle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioPlaybackServiceBehaviorTest {

    // ---- Feature flag gating ----

    @Test
    fun `SEEKBACK_ENABLED is false for initial release`() {
        assertFalse(RadioPlaybackService.SEEKBACK_ENABLED)
    }

    // ---- Intent action constants ----

    @Test
    fun `ACTION_PLAY is correctly namespaced`() {
        assertTrue(RadioPlaybackService.ACTION_PLAY.startsWith("com.cascadiacollections.sir.action."))
    }

    @Test
    fun `ACTION_SEEK_BACK is correctly namespaced`() {
        assertTrue(RadioPlaybackService.ACTION_SEEK_BACK.startsWith("com.cascadiacollections.sir.action."))
    }

    @Test
    fun `ACTION_GO_LIVE is correctly namespaced`() {
        assertTrue(RadioPlaybackService.ACTION_GO_LIVE.startsWith("com.cascadiacollections.sir.action."))
    }

    @Test
    fun `all public action constants are distinct`() {
        val actions = listOf(
            RadioPlaybackService.ACTION_PLAY,
            RadioPlaybackService.ACTION_SEEK_BACK,
            RadioPlaybackService.ACTION_GO_LIVE,
            RadioPlaybackService.ACTION_SET_SLEEP_TIMER,
            RadioPlaybackService.ACTION_SET_EQUALIZER,
            RadioPlaybackService.ACTION_SET_STREAM_QUALITY
        )
        assertEquals(actions.size, actions.toSet().size)
    }

    @Test
    fun `all public extra key constants are distinct`() {
        val extras = listOf(
            RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES,
            RadioPlaybackService.EXTRA_EQUALIZER_PRESET,
            RadioPlaybackService.EXTRA_STREAM_QUALITY
        )
        assertEquals(extras.size, extras.toSet().size)
    }

    // ---- Service creation ----

    @Test
    fun `service can be instantiated`() {
        val service = RadioPlaybackService()
        assertNotNull(service)
    }

    // ---- Sleep timer intent extras ----

    @Test
    fun `sleep timer intent carries correct extras`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_SET_SLEEP_TIMER
            putExtra(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES, 30)
        }
        assertEquals(RadioPlaybackService.ACTION_SET_SLEEP_TIMER, intent.action)
        assertEquals(30, intent.getIntExtra(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES, 0))
    }

    @Test
    fun `equalizer intent carries correct extras`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_SET_EQUALIZER
            putExtra(RadioPlaybackService.EXTRA_EQUALIZER_PRESET, EqualizerPreset.BASS_BOOST.ordinal)
        }
        assertEquals(RadioPlaybackService.ACTION_SET_EQUALIZER, intent.action)
        assertEquals(
            EqualizerPreset.BASS_BOOST.ordinal,
            intent.getIntExtra(RadioPlaybackService.EXTRA_EQUALIZER_PRESET, 0)
        )
    }

    @Test
    fun `stream quality intent carries correct extras`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_SET_STREAM_QUALITY
            putExtra(RadioPlaybackService.EXTRA_STREAM_QUALITY, StreamQuality.LOW.ordinal)
        }
        assertEquals(RadioPlaybackService.ACTION_SET_STREAM_QUALITY, intent.action)
        assertEquals(
            StreamQuality.LOW.ordinal,
            intent.getIntExtra(RadioPlaybackService.EXTRA_STREAM_QUALITY, 0)
        )
    }

    // ---- Replay buffer sizing ----

    @Test
    fun `REPLAY_BUFFER_SIZE holds at least 30 seconds at 64kbps`() {
        // 64kbps = 8000 bytes/sec, 30 sec = 240,000 bytes
        assertTrue(RadioPlaybackService.REPLAY_BUFFER_SIZE >= 240_000)
    }

    @Test
    fun `REPLAY_BUFFER_SIZE holds at least 60 seconds at 64kbps`() {
        // 64kbps = 8000 bytes/sec, 60 sec = 480,000 bytes
        assertTrue(RadioPlaybackService.REPLAY_BUFFER_SIZE >= 480_000)
    }

    // ---- Audio becoming noisy receiver ----

    @Test
    fun `ACTION_AUDIO_BECOMING_NOISY intent has correct action string`() {
        assertEquals("android.media.AUDIO_BECOMING_NOISY", AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    }

    // ---- CAST_MODULE_NAME ----

    @Test
    fun `CAST_MODULE_NAME matches dynamic feature module name`() {
        assertEquals("cast", CastFeatureManager.CAST_MODULE_NAME)
    }

    // ---- Default stream URL ----

    @Test
    fun `default stream URL is valid HTTPS`() {
        assertTrue(StreamConfig.DEFAULT_STREAM_URL.startsWith("https://"))
    }

    @Test
    fun `default stream URL is not blank`() {
        assertTrue(StreamConfig.DEFAULT_STREAM_URL.isNotBlank())
    }
}
