package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Guards the FOSS/Play partition.
 *
 * These assertions are the reason the flavor split exists: if someone moves the Play
 * implementations back into `main`, or adds a Play Core dependency to the base
 * configuration, this suite compiles against the wrong class and fails. It only runs
 * under `testFossDebugUnitTest`, so it has to be in CI to be worth anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FossCastPartitionTest {

    @Test
    fun `cast reports unavailable rather than not-installed`() {
        val manager = CastFeatureManager(RuntimeEnvironment.getApplication())

        // Not NotInstalled: that would render a switch the user could toggle forever
        // without a module ever arriving.
        assertEquals(CastModuleState.Unavailable, manager.moduleState.value)
        assertFalse(manager.isModuleInstalled())
    }

    @Test
    fun `install and retry are inert`() {
        val manager = CastFeatureManager(RuntimeEnvironment.getApplication())

        manager.installCastModule()
        manager.retry()

        // No transition to Installing — there is nothing to install.
        assertEquals(CastModuleState.Unavailable, manager.moduleState.value)
    }

    @Test
    fun `device detection never reports a receiver`() {
        val detector = CastDeviceDetector(RuntimeEnvironment.getApplication())

        assertFalse(detector.castDevicesAvailable.value)
        detector.resetDetection()
        assertFalse(detector.castDevicesAvailable.value)
        detector.release()
    }
}
