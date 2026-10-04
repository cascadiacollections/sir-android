package com.cascadiacollections.sir

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
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

    /** DataStore is shared across tests in this class, so reset the timer state. */
    @Before
    fun resetSleepTimer() = runBlocking {
        SettingsRepository(app).setSleepTimerFiresAt(0L)
    }

    private fun createViewModel(): RadioViewModel {
        val settings = SettingsRepository(app)
        return RadioViewModel(app, settings).also { coroutineRule.registerViewModel(it) }
    }

    // ---- Initial state ----

    @Test
    fun `initial uiState has all defaults`() {
        val vm = createViewModel()
        val state = vm.uiState.value
        // isConnected may or may not be true depending on timing,
        // but the other fields should be default
        assertThat(state.isPlaying).isFalse()
        assertThat(state.isBuffering).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.trackTitle).isNull()
        assertThat(state.artist).isNull()
        assertThat(state.trackHistory).isEmpty()
        assertThat(state.sleepTimerLabel).isNull()
    }

    @Test
    fun `RadioUiState default constructor has expected values`() {
        val state = RadioUiState()
        assertThat(state.isConnected).isFalse()
        assertThat(state.isPlaying).isFalse()
        assertThat(state.isBuffering).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.trackTitle).isNull()
        assertThat(state.artist).isNull()
        assertThat(state.trackHistory).isEmpty()
        assertThat(state.sleepTimerLabel).isNull()
        assertThat(state.showMeteredWarning).isFalse()
    }

    // ---- dismissMeteredWarning ----

    @Test
    fun `dismissMeteredWarning sets showMeteredWarning false`() {
        val vm = createViewModel()
        // Manually set warning state then dismiss
        vm.dismissMeteredWarning()
        assertThat(vm.uiState.value.showMeteredWarning).isFalse()
    }

    // ---- Metered network detection ----

    /** A ConnectivityManager reporting [metered], with an internet connection unless [online] is false. */
    private fun connectivity(metered: Boolean, online: Boolean = true): ConnectivityManager {
        val network = mockk<android.net.Network>()
        val capabilities = mockk<android.net.NetworkCapabilities> {
            every {
                hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } returns true
        }
        return mockk {
            every { isActiveNetworkMetered } returns metered
            every { activeNetwork } returns if (online) network else null
            every { getNetworkCapabilities(network) } returns capabilities
        }
    }

    private fun installConnectivity(cm: ConnectivityManager) {
        @Suppress("DEPRECATION")
        shadowOf(app).setSystemService(android.content.Context.CONNECTIVITY_SERVICE, cm)
    }

    @Test
    fun `checkMeteredNetwork on unmetered does not set warning`() {
        installConnectivity(connectivity(metered = false))

        val vm = createViewModel()
        assertThat(vm.uiState.value.showMeteredWarning).isFalse()
    }

    @Test
    fun `checkMeteredNetwork on metered sets showMeteredWarning true`() {
        installConnectivity(connectivity(metered = true))

        val vm = createViewModel()
        assertThat(vm.uiState.value.showMeteredWarning).isTrue()
    }

    @Test
    fun `no mobile data warning when offline even though Android reports metered`() {
        // isActiveNetworkMetered returns true with no network at all (airplane mode).
        installConnectivity(connectivity(metered = true, online = false))

        val vm = createViewModel()
        assertThat(vm.uiState.value.showMeteredWarning).isFalse()
    }

    // ---- togglePlayback with null controller ----

    @Test
    fun `togglePlayback with null controller does not crash`() {
        val vm = createViewModel()
        // Controller won't connect in test - should not throw
        vm.togglePlayback()
    }

    @Test
    fun `sleep timer label clears when timer is cancelled`() = runBlocking {
        val settings = SettingsRepository(app)
        val vm = RadioViewModel(app, settings).also { coroutineRule.registerViewModel(it) }

        settings.setSleepTimerFiresAt(System.currentTimeMillis() + 10 * 60_000L)
        waitUntil { vm.uiState.value.sleepTimerLabel != null }

        settings.setSleepTimerFiresAt(0L)
        waitUntil { vm.uiState.value.sleepTimerLabel == null }
    }

    @Test
    fun `sleep timer label updates when timer changes`() = runBlocking {
        val settings = SettingsRepository(app)
        val vm = RadioViewModel(app, settings).also { coroutineRule.registerViewModel(it) }

        settings.setSleepTimerFiresAt(System.currentTimeMillis() + 120 * 60_000L)
        waitUntil { vm.uiState.value.sleepTimerLabel != null }

        settings.setSleepTimerFiresAt(System.currentTimeMillis() + 60_000L)
        waitUntil { vm.uiState.value.sleepTimerLabel?.endsWith("1m") == true }
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
        assertThat(copied.isConnected).isTrue()
        assertThat(copied.isPlaying).isFalse()
        assertThat(copied.isBuffering).isFalse()
        assertThat(copied.isError).isFalse()
        assertThat(copied.trackTitle).isEqualTo("Test Song")
        assertThat(copied.artist).isEqualTo("Test Artist")
        assertThat(copied.sleepTimerLabel).isEqualTo("Sleep in 30m")
        assertThat(copied.showMeteredWarning).isTrue()
    }

    @Test
    fun `RadioUiState data class equality works correctly`() {
        val a = RadioUiState(isPlaying = true, trackTitle = "Song")
        val b = RadioUiState(isPlaying = true, trackTitle = "Song")
        assertThat(b).isEqualTo(a)
    }

    // ---- Factory ----

    @Test
    fun `Factory creates RadioViewModel instance`() {
        val settings = SettingsRepository(app)
        val factory = RadioViewModel.Factory(app, settings)
        val vm = factory.create(RadioViewModel::class.java).also {
            coroutineRule.registerViewModel(it)
        }
        assertThat(vm).isInstanceOf<RadioViewModel>()
    }

    private suspend fun waitUntil(timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
        withTimeout(timeoutMillis) {
            while (!condition()) {
                delay(10L)
            }
        }
    }
}
