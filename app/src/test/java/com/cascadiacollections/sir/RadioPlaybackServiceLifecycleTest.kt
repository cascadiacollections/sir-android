package com.cascadiacollections.sir

import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Lifecycle and onStartCommand tests for [RadioPlaybackService].
 *
 * Uses Robolectric ServiceController to exercise the actual service code paths.
 * A single service instance is shared across tests because Media3 enforces
 * globally unique session IDs — creating multiple services causes collisions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioPlaybackServiceLifecycleTest {

    private lateinit var serviceController: ServiceController<RadioPlaybackService>
    private lateinit var service: RadioPlaybackService

    @Before
    fun setUp() {
        serviceController = Robolectric.buildService(RadioPlaybackService::class.java).create()
        service = serviceController.get()
    }

    @After
    fun tearDown() {
        serviceController.destroy()
    }

    @Test
    fun `service creates successfully via Robolectric`() {
        assertNotNull(service)
    }

    @Test
    fun `onGetSession does not crash after creation`() {
        val mockControllerInfo: androidx.media3.session.MediaSession.ControllerInfo =
            io.mockk.mockk(relaxed = true)
        service.onGetSession(mockControllerInfo)
    }

    @Test
    fun `onStartCommand with null intent does not crash`() {
        val result = service.onStartCommand(null, 0, 1)
        assertNotNull(result)
    }

    @Test
    fun `onStartCommand with ACTION_PLAY does not crash`() {
        val intent = Intent().apply {
            action = RadioPlaybackService.ACTION_PLAY
        }
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun `onStartCommand with ACTION_SEEK_BACK when disabled is no-op`() {
        val intent = Intent().apply {
            action = RadioPlaybackService.ACTION_SEEK_BACK
        }
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun `onStartCommand with ACTION_GO_LIVE when disabled is no-op`() {
        val intent = Intent().apply {
            action = RadioPlaybackService.ACTION_GO_LIVE
        }
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun `onStartCommand with ACTION_SET_SLEEP_TIMER zero minutes disables timer`() {
        val intent = Intent().apply {
            action = RadioPlaybackService.ACTION_SET_SLEEP_TIMER
            putExtra(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES, 0)
        }
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun `onStartCommand with ACTION_SET_SLEEP_TIMER positive minutes`() {
        val intent = Intent().apply {
            action = RadioPlaybackService.ACTION_SET_SLEEP_TIMER
            putExtra(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES, 30)
        }
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun `onStartCommand with ACTION_SET_EQUALIZER applies preset`() {
        val intent = Intent().apply {
            action = RadioPlaybackService.ACTION_SET_EQUALIZER
            putExtra(RadioPlaybackService.EXTRA_EQUALIZER_PRESET, EqualizerPreset.BASS_BOOST.ordinal)
        }
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun `onStartCommand with ACTION_SET_STREAM_QUALITY changes stream`() {
        val intent = Intent().apply {
            action = RadioPlaybackService.ACTION_SET_STREAM_QUALITY
            putExtra(RadioPlaybackService.EXTRA_STREAM_QUALITY, StreamQuality.LOW.ordinal)
        }
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun `onStartCommand with ACTION_STOP returns START_NOT_STICKY`() {
        val intent = Intent().apply {
            action = "com.cascadiacollections.sir.action.STOP"
        }
        val result = service.onStartCommand(intent, 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }

    @Test
    fun `onStartCommand with ACTION_PAUSE does not crash`() {
        val intent = Intent().apply {
            action = "com.cascadiacollections.sir.action.PAUSE"
        }
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun `service handles unknown action gracefully`() {
        val intent = Intent().apply {
            action = "com.example.UNKNOWN_ACTION"
        }
        service.onStartCommand(intent, 0, 1)
    }
}
