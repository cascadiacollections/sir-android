package com.cascadiacollections.sir

import android.app.Application
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.persistence.SettingsRepository
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
        return RadioViewModel(app, settings).also { coroutineRule.registerViewModel(it) }
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
        assertThat(updated.isConnected).isTrue()
        assertThat(updated.isPlaying).isTrue()
        assertThat(updated.isBuffering).isFalse()
        assertThat(updated.isError).isFalse()
        assertThat(updated.trackTitle).isEqualTo("Test")
        assertThat(updated.artist).isEqualTo("Artist")
        assertThat(updated.sleepTimerLabel).isNull()
        assertThat(updated.showMeteredWarning).isFalse()
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
        assertThat(state.isConnected).isTrue()
        assertThat(state.isPlaying).isTrue()
        assertThat(state.isBuffering).isTrue()
        assertThat(state.isError).isTrue()
        assertThat(state.trackTitle).isEqualTo("Song")
        assertThat(state.artist).isEqualTo("Band")
        assertThat(state.sleepTimerLabel).isEqualTo("5m")
        assertThat(state.showMeteredWarning).isTrue()
    }

    @Test
    fun `RadioUiState hashCode is consistent with equals`() {
        val a = RadioUiState(isPlaying = true, trackTitle = "Song")
        val b = RadioUiState(isPlaying = true, trackTitle = "Song")
        assertThat(b.hashCode()).isEqualTo(a.hashCode())
    }

    @Test
    fun `RadioUiState toString contains field values`() {
        val state = RadioUiState(trackTitle = "MyTrack")
        assertThat(state.toString()).contains("MyTrack")
    }

    // ---- ViewModel state ----

    @Test
    fun `dismissMeteredWarning is idempotent`() {
        val vm = createViewModel()
        vm.dismissMeteredWarning()
        vm.dismissMeteredWarning()
        assertThat(vm.uiState.value.showMeteredWarning).isFalse()
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
        assertThat(state.isPlaying).isFalse()
        assertThat(state.isBuffering).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.trackTitle).isNull()
        assertThat(state.artist).isNull()
    }

    // ---- Factory ----

    @Test
    fun `Factory creates correct type`() {
        val settings = SettingsRepository(app)
        val factory = RadioViewModel.Factory(app, settings)
        val vm = factory.create(RadioViewModel::class.java).also {
            coroutineRule.registerViewModel(it)
        }
        assertThat(vm::class.java).isEqualTo(RadioViewModel::class.java)
    }

    @Test
    fun `checkMeteredNetwork with no connectivity manager does not crash`() {
        // Clear system service to simulate null ConnectivityManager
        val shadowApp = shadowOf(app)
        @Suppress("DEPRECATION")
        shadowApp.setSystemService(android.content.Context.CONNECTIVITY_SERVICE, null)
        // Should not crash
        val vm = createViewModel()
        assertThat(vm.uiState.value.showMeteredWarning).isFalse()
    }
}
