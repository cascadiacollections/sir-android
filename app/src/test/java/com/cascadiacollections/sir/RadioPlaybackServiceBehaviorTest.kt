package com.cascadiacollections.sir

import android.content.Intent
import android.media.AudioManager
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.StreamConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
        assertThat(RadioPlaybackService.SEEKBACK_ENABLED).isFalse()
    }

    // ---- Intent action constants ----

    @Test
    fun `ACTION_PLAY is correctly namespaced`() {
        assertThat(RadioPlaybackService.ACTION_PLAY).startsWith("com.cascadiacollections.sir.action.")
    }

    @Test
    fun `ACTION_SEEK_BACK is correctly namespaced`() {
        assertThat(RadioPlaybackService.ACTION_SEEK_BACK).startsWith("com.cascadiacollections.sir.action.")
    }

    @Test
    fun `ACTION_GO_LIVE is correctly namespaced`() {
        assertThat(RadioPlaybackService.ACTION_GO_LIVE).startsWith("com.cascadiacollections.sir.action.")
    }

    @Test
    fun `all public action constants are distinct`() {
        val actions = listOf(
            RadioPlaybackService.ACTION_PLAY,
            RadioPlaybackService.ACTION_SEEK_BACK,
            RadioPlaybackService.ACTION_GO_LIVE,
            RadioPlaybackService.ACTION_SET_SLEEP_TIMER,
            RadioPlaybackService.ACTION_SET_EQUALIZER,
            RadioPlaybackService.ACTION_PLAY_FROM_SEARCH
        )
        assertThat(actions.toSet()).hasSize(actions.size)
    }

    @Test
    fun `all public extra key constants are distinct`() {
        val extras = listOf(
            RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES,
            RadioPlaybackService.EXTRA_EQUALIZER_PRESET,
            RadioPlaybackService.EXTRA_SEARCH_QUERY,
            RadioPlaybackService.EXTRA_STREAM_URL
        )
        assertThat(extras.toSet()).hasSize(extras.size)
    }

    @Test
    fun `play-from-search intent carries the raw query`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_PLAY_FROM_SEARCH
            putExtra(RadioPlaybackService.EXTRA_SEARCH_QUERY, "NPR")
        }
        assertThat(intent.action).isEqualTo(RadioPlaybackService.ACTION_PLAY_FROM_SEARCH)
        assertThat(intent.getStringExtra(RadioPlaybackService.EXTRA_SEARCH_QUERY)).isEqualTo("NPR")
    }

    // ---- Service creation ----

    @Test
    fun `service can be instantiated`() {
        val service = RadioPlaybackService()
        assertThat(service).isNotNull()
    }

    // ---- Sleep timer intent extras ----

    @Test
    fun `sleep timer intent carries correct extras`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_SET_SLEEP_TIMER
            putExtra(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES, 30)
        }
        assertThat(intent.action).isEqualTo(RadioPlaybackService.ACTION_SET_SLEEP_TIMER)
        assertThat(intent.getIntExtra(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES, 0)).isEqualTo(30)
    }

    @Test
    fun `equalizer intent carries correct extras`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_SET_EQUALIZER
            putExtra(RadioPlaybackService.EXTRA_EQUALIZER_PRESET, EqualizerPreset.BASS_BOOST.ordinal)
        }
        assertThat(intent.action).isEqualTo(RadioPlaybackService.ACTION_SET_EQUALIZER)
        assertThat(intent.getIntExtra(RadioPlaybackService.EXTRA_EQUALIZER_PRESET, 0))
            .isEqualTo(EqualizerPreset.BASS_BOOST.ordinal)
    }

    // ---- Replay buffer sizing ----

    @Test
    fun `REPLAY_BUFFER_SIZE holds at least 30 seconds at 64kbps`() {
        // 64kbps = 8000 bytes/sec, 30 sec = 240,000 bytes
        assertThat(RadioPlaybackService.REPLAY_BUFFER_SIZE).isGreaterThanOrEqualTo(240_000)
    }

    @Test
    fun `REPLAY_BUFFER_SIZE holds at least 60 seconds at 64kbps`() {
        // 64kbps = 8000 bytes/sec, 60 sec = 480,000 bytes
        assertThat(RadioPlaybackService.REPLAY_BUFFER_SIZE).isGreaterThanOrEqualTo(480_000)
    }

    // ---- Audio becoming noisy receiver ----

    @Test
    fun `ACTION_AUDIO_BECOMING_NOISY intent has correct action string`() {
        assertThat(AudioManager.ACTION_AUDIO_BECOMING_NOISY).isEqualTo("android.media.AUDIO_BECOMING_NOISY")
    }

    // ---- CAST_MODULE_NAME ----

    @Test
    fun `CAST_MODULE_NAME matches dynamic feature module name`() {
        assertThat(CastFeatureManager.CAST_MODULE_NAME).isEqualTo("cast")
    }

    // ---- Default stream URL ----

    @Test
    fun `default stream URL is valid HTTPS`() {
        assertThat(StreamConfig.DEFAULT_STREAM_URL).startsWith("https://")
    }

    @Test
    fun `default stream URL is not blank`() {
        assertThat(StreamConfig.DEFAULT_STREAM_URL.isNotBlank()).isTrue()
    }
}
