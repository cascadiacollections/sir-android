package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
        assertThat(detector.castDevicesAvailable.value).isFalse()
    }

    @Test
    fun `resetDetection sets castDevicesAvailable to false`() {
        val detector = createDetector()
        detector.resetDetection()
        assertThat(detector.castDevicesAvailable.value).isFalse()
    }

    @Test
    fun `release does not crash when called before any scanning`() {
        val detector = createDetector()
        detector.release()
        // Should not throw
        assertThat(detector.castDevicesAvailable.value).isFalse()
    }

    @Test
    fun `release can be called multiple times safely`() {
        val detector = createDetector()
        detector.release()
        detector.release()
        assertThat(detector.castDevicesAvailable.value).isFalse()
    }

    @Test
    fun `onPause does not crash when called before onResume`() {
        val detector = createDetector()
        detector.onPause(mockLifecycleOwner())
        assertThat(detector.castDevicesAvailable.value).isFalse()
    }

    @Test
    fun `castDevicesAvailable remains false after resetDetection`() {
        val detector = createDetector()
        // Even after multiple resets, should stay false
        detector.resetDetection()
        detector.resetDetection()
        assertThat(detector.castDevicesAvailable.value).isFalse()
    }

    private fun mockLifecycleOwner(): androidx.lifecycle.LifecycleOwner = io.mockk.mockk(relaxed = true)
}
