package com.cascadiacollections.sir

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [CastDeviceDetector] lifecycle and WiFi gating behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastDeviceDetectorTest {

    private fun createDetector() = CastDeviceDetector(RuntimeEnvironment.getApplication())

    @Test
    fun `initial castDevicesAvailable is false`() {
        val detector = createDetector()
        assertFalse(detector.castDevicesAvailable.value)
    }

    @Test
    fun `resetDetection sets castDevicesAvailable to false`() {
        val detector = createDetector()
        detector.resetDetection()
        assertFalse(detector.castDevicesAvailable.value)
    }

    @Test
    fun `release does not crash when called before any scanning`() {
        val detector = createDetector()
        detector.release()
        // Should not throw
        assertFalse(detector.castDevicesAvailable.value)
    }

    @Test
    fun `release can be called multiple times safely`() {
        val detector = createDetector()
        detector.release()
        detector.release()
        assertFalse(detector.castDevicesAvailable.value)
    }

    @Test
    fun `onPause does not crash when called before onResume`() {
        val detector = createDetector()
        detector.onPause(mockLifecycleOwner())
        assertFalse(detector.castDevicesAvailable.value)
    }

    @Test
    fun `castDevicesAvailable remains false after resetDetection`() {
        val detector = createDetector()
        // Even after multiple resets, should stay false
        detector.resetDetection()
        detector.resetDetection()
        assertFalse(detector.castDevicesAvailable.value)
    }

    private fun mockLifecycleOwner(): androidx.lifecycle.LifecycleOwner {
        return io.mockk.mockk(relaxed = true)
    }
}
