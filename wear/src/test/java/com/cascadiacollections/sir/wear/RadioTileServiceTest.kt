package com.cascadiacollections.sir.wear

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [RadioTileService].
 *
 * Robolectric has no shadow for `androidx.wear.tiles.TileService` (only for the unrelated
 * `android.service.quicksettings.TileService`). Building/creating the service — needed to give
 * it a live `Context` for [RadioTileService.onTileRequest]/[RadioTileService.onTileResourcesRequest] —
 * makes the JVM resolve `TileService`'s full method table, which references
 * `com.google.wear.services.tiles.TileInstance`: a real Wear OS system-image class that isn't on
 * the unit test classpath, so it fails with `NoClassDefFoundError` regardless of the configured
 * SDK level. Exercising the tile/resources request logic therefore needs a real device or Wear
 * emulator (see the plan's verification section); this suite only covers what's reachable
 * without a live Context.
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
    fun `onDestroy without pending work does not crash`() {
        val service = RadioTileService()
        service.onDestroy()
    }

    @Test
    fun `onDestroy can be called multiple times`() {
        val service = RadioTileService()
        service.onDestroy()
        service.onDestroy()
    }
}
