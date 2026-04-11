package com.cascadiacollections.sir

import android.app.Application
import android.net.ConnectivityManager
import io.mockk.every
import io.mockk.mockk
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
 * Extended tests for [RadioViewModel] to improve state management coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioViewModelExtendedTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private fun createViewModel(): RadioViewModel {
        val settings = SettingsRepository(app)
        return RadioViewModel(app, settings)
    }

    // ---- RadioUiState field-level tests ----

    @Test
    fun `RadioUiState copy updates only specified fields`() {
        val original = RadioUiState()
        val updated = original.copy(
            isConnected = true,
            isPlaying = true,
            trackTitle = "Test",
            artist = "Artist"
        )
        assertTrue(updated.isConnected)
        assertTrue(updated.isPlaying)
        assertFalse(updated.isBuffering)
        assertFalse(updated.isError)
        assertEquals("Test", updated.trackTitle)
        assertEquals("Artist", updated.artist)
        assertNull(updated.sleepTimerLabel)
        assertFalse(updated.showMeteredWarning)
    }

    @Test
    fun `RadioUiState with all fields set`() {
        val state = RadioUiState(
            isConnected = true,
            isPlaying = true,
            isBuffering = true,
            isError = true,
            trackTitle = "Song",
            artist = "Band",
            sleepTimerLabel = "5m",
            showMeteredWarning = true
        )
        assertTrue(state.isConnected)
        assertTrue(state.isPlaying)
        assertTrue(state.isBuffering)
        assertTrue(state.isError)
        assertEquals("Song", state.trackTitle)
        assertEquals("Band", state.artist)
        assertEquals("5m", state.sleepTimerLabel)
        assertTrue(state.showMeteredWarning)
    }

    @Test
    fun `RadioUiState hashCode is consistent with equals`() {
        val a = RadioUiState(isPlaying = true, trackTitle = "Song")
        val b = RadioUiState(isPlaying = true, trackTitle = "Song")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `RadioUiState toString contains field values`() {
        val state = RadioUiState(trackTitle = "MyTrack")
        assertTrue(state.toString().contains("MyTrack"))
    }

    // ---- ViewModel state ----

    @Test
    fun `dismissMeteredWarning is idempotent`() {
        val vm = createViewModel()
        vm.dismissMeteredWarning()
        vm.dismissMeteredWarning()
        assertFalse(vm.uiState.value.showMeteredWarning)
    }

    @Test
    fun `togglePlayback multiple times without crash`() {
        val vm = createViewModel()
        // Without a connected controller, these should be safe no-ops
        vm.togglePlayback()
        vm.togglePlayback()
        vm.togglePlayback()
    }

    @Test
    fun `uiState flow initial value has default fields`() {
        val vm = createViewModel()
        val state = vm.uiState.value
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertFalse(state.isError)
        assertNull(state.trackTitle)
        assertNull(state.artist)
    }

    // ---- Factory ----

    @Test
    fun `Factory creates correct type`() {
        val settings = SettingsRepository(app)
        val factory = RadioViewModel.Factory(app, settings)
        val vm = factory.create(RadioViewModel::class.java)
        assertEquals(RadioViewModel::class.java, vm::class.java)
    }

    @Test
    fun `checkMeteredNetwork with no connectivity manager does not crash`() {
        // Clear system service to simulate null ConnectivityManager
        val shadowApp = shadowOf(app)
        @Suppress("DEPRECATION")
        shadowApp.setSystemService(android.content.Context.CONNECTIVITY_SERVICE, null)
        // Should not crash
        val vm = createViewModel()
        assertFalse(vm.uiState.value.showMeteredWarning)
    }
}
