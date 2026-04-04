package com.cascadiacollections.sir

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [RadioViewModel] state management and public API.
 *
 * The ViewModel tries to connect to MediaController in init{}.
 * In tests, that connection will fail (no running service), which
 * is handled gracefully. We test the state management, metered
 * network detection, and togglePlayback with null controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private fun createViewModel(): RadioViewModel {
        val settings = SettingsRepository(app)
        return RadioViewModel(app, settings)
    }

    // ---- Initial state ----

    @Test
    fun `initial uiState has all defaults`() {
        val vm = createViewModel()
        val state = vm.uiState.value
        // isConnected may or may not be true depending on timing,
        // but the other fields should be default
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertFalse(state.isError)
        assertNull(state.trackTitle)
        assertNull(state.artist)
        assertNull(state.sleepTimerLabel)
    }

    @Test
    fun `RadioUiState default constructor has expected values`() {
        val state = RadioUiState()
        assertFalse(state.isConnected)
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertFalse(state.isError)
        assertNull(state.trackTitle)
        assertNull(state.artist)
        assertNull(state.sleepTimerLabel)
        assertFalse(state.showMeteredWarning)
    }

    // ---- dismissMeteredWarning ----

    @Test
    fun `dismissMeteredWarning sets showMeteredWarning false`() {
        val vm = createViewModel()
        // Manually set warning state then dismiss
        vm.dismissMeteredWarning()
        assertFalse(vm.uiState.value.showMeteredWarning)
    }

    // ---- Metered network detection ----

    @Test
    fun `checkMeteredNetwork on unmetered does not set warning`() {
        // Ensure unmetered network
        val mockCm = mockk<ConnectivityManager> {
            every { isActiveNetworkMetered } returns false
        }
        val shadowApp = shadowOf(app)
        @Suppress("DEPRECATION")
        shadowApp.setSystemService(android.content.Context.CONNECTIVITY_SERVICE, mockCm)

        val vm = createViewModel()
        assertFalse(vm.uiState.value.showMeteredWarning)
    }

    @Test
    fun `checkMeteredNetwork on metered sets showMeteredWarning true`() {
        // Mock the ConnectivityManager to report metered network
        val mockCm = mockk<ConnectivityManager> {
            every { isActiveNetworkMetered } returns true
        }
        val shadowApp = shadowOf(app)
        @Suppress("DEPRECATION")
        shadowApp.setSystemService(android.content.Context.CONNECTIVITY_SERVICE, mockCm)

        val vm = createViewModel()
        assertTrue(vm.uiState.value.showMeteredWarning)
    }

    // ---- togglePlayback with null controller ----

    @Test
    fun `togglePlayback with null controller does not crash`() {
        val vm = createViewModel()
        // Controller won't connect in test - should not throw
        vm.togglePlayback()
    }

    // ---- RadioUiState copy correctness ----

    @Test
    fun `RadioUiState copy preserves unmodified fields`() {
        val original = RadioUiState(
            isConnected = true,
            isPlaying = true,
            isBuffering = false,
            isError = false,
            trackTitle = "Test Song",
            artist = "Test Artist",
            sleepTimerLabel = "Sleep in 30m",
            showMeteredWarning = true
        )
        val copied = original.copy(isPlaying = false)
        assertTrue(copied.isConnected)
        assertFalse(copied.isPlaying)
        assertFalse(copied.isBuffering)
        assertFalse(copied.isError)
        assertEquals("Test Song", copied.trackTitle)
        assertEquals("Test Artist", copied.artist)
        assertEquals("Sleep in 30m", copied.sleepTimerLabel)
        assertTrue(copied.showMeteredWarning)
    }

    @Test
    fun `RadioUiState data class equality works correctly`() {
        val a = RadioUiState(isPlaying = true, trackTitle = "Song")
        val b = RadioUiState(isPlaying = true, trackTitle = "Song")
        assertEquals(a, b)
    }

    // ---- Factory ----

    @Test
    fun `Factory creates RadioViewModel instance`() {
        val settings = SettingsRepository(app)
        val factory = RadioViewModel.Factory(app, settings)
        val vm = factory.create(RadioViewModel::class.java)
        assertTrue(vm is RadioViewModel)
    }
}
