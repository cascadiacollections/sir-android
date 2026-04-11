package com.cascadiacollections.sir

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [ServiceUtils] extension function.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceUtilsTest {

    @Test
    fun `ensureRadioServiceRunning does not crash`() {
        val context = RuntimeEnvironment.getApplication()
        // Should not throw — the service won't actually start in Robolectric
        // but the intent will be recorded
        context.ensureRadioServiceRunning()
    }

    @Test
    fun `ensureRadioServiceRunning creates correct intent`() {
        val context = RuntimeEnvironment.getApplication()
        context.ensureRadioServiceRunning()
        val shadow = org.robolectric.Shadows.shadowOf(context)
        val intent = shadow.nextStartedService
        assertNotNull(intent)
        assertEquals(
            RadioPlaybackService::class.java.name,
            intent.component?.className
        )
    }
}
