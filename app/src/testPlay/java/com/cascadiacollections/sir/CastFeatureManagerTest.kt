package com.cascadiacollections.sir

import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.google.android.gms.tasks.Task as GmsTask
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [CastFeatureManager] state machine: module installation,
 * progress tracking, failure, and cancellation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastFeatureManagerTest {

    private lateinit var mockSplitInstallManager: SplitInstallManager
    private lateinit var listenerSlot: CapturingSlot<SplitInstallStateUpdatedListener>

    @Before
    fun setUp() {
        listenerSlot = slot()
        mockSplitInstallManager = mockk(relaxed = true) {
            every { installedModules } returns emptySet()
            every { registerListener(capture(listenerSlot)) } returns Unit
        }
        mockkStatic(SplitInstallManagerFactory::class)
        every { SplitInstallManagerFactory.create(any()) } returns mockSplitInstallManager
    }

    @After
    fun tearDown() {
        unmockkStatic(SplitInstallManagerFactory::class)
    }

    private fun createManager() = CastFeatureManager(RuntimeEnvironment.getApplication())

    @Test
    fun `initial state is NotInstalled when module not installed`() {
        val manager = createManager()
        assertEquals(CastModuleState.NotInstalled, manager.moduleState.value)
    }

    @Test
    fun `initial state is Installed when module already installed`() {
        every { mockSplitInstallManager.installedModules } returns setOf("cast")
        val manager = createManager()
        assertEquals(CastModuleState.Installed, manager.moduleState.value)
    }

    @Test
    fun `isModuleInstalled returns false when not installed`() {
        val manager = createManager()
        assertFalse(manager.isModuleInstalled())
    }

    @Test
    fun `isModuleInstalled returns true when installed`() {
        every { mockSplitInstallManager.installedModules } returns setOf("cast")
        val manager = createManager()
        assertTrue(manager.isModuleInstalled())
    }

    @Test
    fun `installCastModule when already installed transitions to Installed`() {
        every { mockSplitInstallManager.installedModules } returns setOf("cast")
        val manager = createManager()
        manager.installCastModule()
        assertEquals(CastModuleState.Installed, manager.moduleState.value)
    }

    @Test
    fun `installCastModule sets Installing with 0 progress`() {
        val mockTask = mockk<GmsTask<Int>>(relaxed = true)
        every { mockSplitInstallManager.startInstall(any<SplitInstallRequest>()) } returns mockTask

        val manager = createManager()
        manager.installCastModule()
        assertEquals(CastModuleState.Installing(0f), manager.moduleState.value)
    }

    @Test
    fun `installCastModule when already installing is no-op`() {
        val mockTask = mockk<GmsTask<Int>>(relaxed = true)
        every { mockSplitInstallManager.startInstall(any<SplitInstallRequest>()) } returns mockTask

        val manager = createManager()
        manager.installCastModule()
        assertEquals(CastModuleState.Installing(0f), manager.moduleState.value)

        // Call again — should not change state or call startInstall again
        manager.installCastModule()
        verify(exactly = 1) { mockSplitInstallManager.startInstall(any<SplitInstallRequest>()) }
    }

    @Test
    fun `retry resets to NotInstalled then installs`() {
        val mockTask = mockk<GmsTask<Int>>(relaxed = true)
        every { mockSplitInstallManager.startInstall(any<SplitInstallRequest>()) } returns mockTask

        val manager = createManager()
        manager.retry()
        // After retry, should be in Installing state
        assertEquals(CastModuleState.Installing(0f), manager.moduleState.value)
    }

    @Test
    fun `release unregisters listener`() {
        val manager = createManager()
        manager.release()
        verify { mockSplitInstallManager.unregisterListener(any()) }
    }

    @Test
    fun `CAST_MODULE_NAME is cast`() {
        assertEquals("cast", CastFeatureManager.CAST_MODULE_NAME)
    }
}
