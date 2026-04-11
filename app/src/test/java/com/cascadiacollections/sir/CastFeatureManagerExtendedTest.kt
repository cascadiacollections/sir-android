package com.cascadiacollections.sir

import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.google.android.gms.tasks.Task as GmsTask
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Extended tests for [CastFeatureManager] state transitions
 * driven by the SplitInstallStateUpdatedListener.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastFeatureManagerExtendedTest {

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

    private fun createManager(): CastFeatureManager {
        val manager = CastFeatureManager(RuntimeEnvironment.getApplication())
        return manager
    }

    private fun mockState(
        sessionId: Int,
        status: Int,
        bytesDownloaded: Long = 0,
        totalBytes: Long = 0,
        errorCode: Int = 0
    ): SplitInstallSessionState = mockk {
        every { sessionId() } returns sessionId
        every { status() } returns status
        every { bytesDownloaded() } returns bytesDownloaded
        every { totalBytesToDownload() } returns totalBytes
        every { errorCode() } returns errorCode
    }

    @Test
    fun `listener DOWNLOADING state updates progress`() {
        val mockTask = mockk<GmsTask<Int>>(relaxed = true)
        every { mockSplitInstallManager.startInstall(any<SplitInstallRequest>()) } returns mockTask

        val manager = createManager()
        manager.installCastModule()

        // sessionId defaults to 0 in CastFeatureManager; the addOnSuccessListener
        // callback is not invoked in this test, so we match on the default value.
        assertTrue(listenerSlot.isCaptured)
        val state = mockState(
            sessionId = 0,
            status = SplitInstallSessionStatus.DOWNLOADING,
            bytesDownloaded = 50,
            totalBytes = 100
        )
        listenerSlot.captured.onStateUpdate(state)
        assertEquals(CastModuleState.Installing(0.5f), manager.moduleState.value)
    }

    @Test
    fun `listener INSTALLED state transitions to Installed`() {
        val manager = createManager()
        assertTrue(listenerSlot.isCaptured)

        val state = mockState(
            sessionId = 0,
            status = SplitInstallSessionStatus.INSTALLED
        )
        listenerSlot.captured.onStateUpdate(state)
        assertEquals(CastModuleState.Installed, manager.moduleState.value)
    }

    @Test
    fun `listener FAILED state transitions to Failed with error code`() {
        val manager = createManager()
        assertTrue(listenerSlot.isCaptured)

        val state = mockState(
            sessionId = 0,
            status = SplitInstallSessionStatus.FAILED,
            errorCode = 42
        )
        listenerSlot.captured.onStateUpdate(state)
        assertEquals(CastModuleState.Failed(42), manager.moduleState.value)
    }

    @Test
    fun `listener CANCELED state transitions to NotInstalled`() {
        val manager = createManager()
        assertTrue(listenerSlot.isCaptured)

        val state = mockState(
            sessionId = 0,
            status = SplitInstallSessionStatus.CANCELED
        )
        listenerSlot.captured.onStateUpdate(state)
        assertEquals(CastModuleState.NotInstalled, manager.moduleState.value)
    }

    @Test
    fun `listener INSTALLING state sets progress to 1`() {
        val manager = createManager()
        assertTrue(listenerSlot.isCaptured)

        val state = mockState(
            sessionId = 0,
            status = SplitInstallSessionStatus.INSTALLING
        )
        listenerSlot.captured.onStateUpdate(state)
        assertEquals(CastModuleState.Installing(1f), manager.moduleState.value)
    }

    @Test
    fun `listener ignores events for wrong session id`() {
        val mockTask = mockk<GmsTask<Int>>(relaxed = true)
        every { mockSplitInstallManager.startInstall(any<SplitInstallRequest>()) } returns mockTask

        val manager = createManager()
        manager.installCastModule()

        // Send state for a different session ID
        val state = mockState(
            sessionId = 999,
            status = SplitInstallSessionStatus.INSTALLED
        )
        listenerSlot.captured.onStateUpdate(state)

        // State should still be Installing(0f), not Installed
        assertEquals(CastModuleState.Installing(0f), manager.moduleState.value)
    }

    @Test
    fun `listener PENDING state with 0 total bytes sets 0 progress`() {
        val manager = createManager()
        assertTrue(listenerSlot.isCaptured)

        val state = mockState(
            sessionId = 0,
            status = SplitInstallSessionStatus.PENDING,
            bytesDownloaded = 0,
            totalBytes = 0
        )
        listenerSlot.captured.onStateUpdate(state)
        assertEquals(CastModuleState.Installing(0f), manager.moduleState.value)
    }
}
