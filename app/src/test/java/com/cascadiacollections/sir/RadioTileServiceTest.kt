package com.cascadiacollections.sir

import android.content.Intent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [RadioTileService] quick settings tile behavior.
 *
 * The tile connects to MediaController in onStartListening() which requires
 * a running MediaSession. Tests here exercise the null/disconnected paths
 * that don't require full Media3 infrastructure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioTileServiceTest {

    @Test
    fun `service can be instantiated`() {
        val service = RadioTileService()
        assertNotNull(service)
    }

    @Test
    fun `onStopListening with null controller does not crash`() {
        val service = RadioTileService()
        // controller is null by default — onStopListening should be safe
        service.onStopListening()
    }

    @Test
    fun `onStopListening can be called multiple times`() {
        val service = RadioTileService()
        service.onStopListening()
        service.onStopListening()
    }

    @Test
    fun `onClick with null controller starts foreground service`() {
        val controller = Robolectric.buildService(RadioTileService::class.java)
            .create()
            .get()

        // onClick with null controller (no session connected) should
        // call ensureRadioServiceRunning and startForegroundService
        controller.onClick()

        val shadow = shadowOf(RuntimeEnvironment.getApplication())
        val startedService = shadow.nextStartedService
        assertNotNull("Service should have been started", startedService)
    }

    @Test
    fun `onClick with null controller sends ACTION_PLAY intent`() {
        val controller = Robolectric.buildService(RadioTileService::class.java)
            .create()
            .get()

        controller.onClick()

        val shadow = shadowOf(RuntimeEnvironment.getApplication())
        // Two intents may be sent: ensureRadioServiceRunning + explicit ACTION_PLAY
        val intents = mutableListOf<Intent>()
        var intent = shadow.nextStartedService
        while (intent != null) {
            intents.add(intent)
            intent = shadow.nextStartedService
        }

        val actionPlayIntent = intents.find { it.action == RadioPlaybackService.ACTION_PLAY }
        assertNotNull("Should send ACTION_PLAY intent", actionPlayIntent)
    }
}
