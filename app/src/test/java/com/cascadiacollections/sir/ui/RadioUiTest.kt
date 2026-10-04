package com.cascadiacollections.sir.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.StreamFailure
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [RadioUi].
 * Validates rendering for different playback states.
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized window, so the whole Now Playing column is on screen without scrolling.
@Config(sdk = [34], qualifiers = "w411dp-h914dp")
class RadioUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `displays station name when idle`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = false,
                    isBuffering = false,
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("SIR").assertIsDisplayed()
    }

    @Test
    fun `displays play icon when not playing`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = false,
                    isBuffering = false,
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun `displays pause icon when playing`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = true,
                    isBuffering = false,
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `shows connecting badge when not connected`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = false,
                    isPlaying = false,
                    isBuffering = false,
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("Connecting…").assertIsDisplayed()
    }

    @Test
    fun `shows connecting badge and cancel action when buffering`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = false,
                    isBuffering = true,
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("Connecting…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cancel connection").assertIsDisplayed()
    }

    @Test
    fun `untyped error shows the generic stream error copy and a retry action`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = false,
                    isBuffering = false,
                    isError = true,
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("Stream error").assertIsDisplayed()
        composeRule.onNodeWithText("The stream stopped unexpectedly. Tap to retry.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Retry").assertIsDisplayed()
    }

    @Test
    fun `shows reconnecting when error and buffering`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = false,
                    isBuffering = true,
                    isError = true,
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("Reconnecting…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cancel connection").assertIsDisplayed()
    }

    @Test
    fun `typed failures use ShoutKit copy`() {
        val cases = listOf(
            StreamFailure.NoNetwork to ("No connection" to "No internet connection. Check your network and try again."),
            StreamFailure.StationUnavailable(404) to
                ("Unavailable" to "This station isn't available right now. Try another station."),
            StreamFailure.Unplayable to ("Stream error" to "The stream stopped unexpectedly. Tap to retry."),
            StreamFailure.Stalled to ("Stream stalled" to "The stream stopped responding. Tap to retry.")
        )
        var failure by mutableStateOf<StreamFailure?>(null)
        composeRule.setContent {
            SirTheme {
                ListenScreen(
                    state = RadioUiState(isConnected = true, failure = failure),
                    onToggle = {}
                )
            }
        }
        cases.forEach { (typed, copy) ->
            failure = typed
            composeRule.onNodeWithText(copy.first).assertIsDisplayed()
            composeRule.onNodeWithText(copy.second).assertIsDisplayed()
        }
    }

    @Test
    fun `shows station name and genre when nothing is resolved`() {
        composeRule.setContent {
            SirTheme {
                ListenScreen(
                    state = RadioUiState(
                        isConnected = true,
                        isPlaying = true,
                        station = Station(id = "kexp", name = "KEXP", url = "https://k", tags = "indie,rock")
                    ),
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("KEXP").assertIsDisplayed()
        composeRule.onNodeWithText("Indie").assertIsDisplayed()
        composeRule.onNodeWithText("Live").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `falls back to live radio for the app's own stream and disables the heart`() {
        composeRule.setContent {
            SirTheme {
                ListenScreen(state = RadioUiState(isConnected = true), onToggle = {})
            }
        }
        composeRule.onNodeWithText("SIR").assertIsDisplayed()
        composeRule.onNodeWithText("Live radio").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Favorite").assertIsNotEnabled()
    }

    @Test
    fun `heart toggles a directory station and announces its state`() {
        var toggled = false
        composeRule.setContent {
            SirTheme {
                ListenScreen(
                    state = RadioUiState(
                        isConnected = true,
                        station = Station(id = "a", name = "A", url = "https://a"),
                        isFavorite = true
                    ),
                    onToggleFavorite = { toggled = true },
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Favorite")
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Saved to favorites"))
            .performClick()
        assertTrue(toggled)
    }

    @Test
    fun `apple music overflow only appears with a track page`() {
        var url by mutableStateOf<String?>(null)
        composeRule.setContent {
            SirTheme {
                ListenScreen(
                    state = RadioUiState(isConnected = true, trackViewUrl = url),
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
        url = "https://music.apple.com/us/album/x"
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Open in Apple Music").assertIsDisplayed()
    }

    @Test
    fun `shows track title and artist when playing with metadata`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = true,
                    isBuffering = false,
                    trackTitle = "Sweet Home Alabama",
                    artist = "Lynyrd Skynyrd",
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("Sweet Home Alabama — Lynyrd Skynyrd").assertIsDisplayed()
    }

    @Test
    fun `shows sleep timer label when present`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = true,
                    isBuffering = false,
                    sleepTimerLabel = "Sleep in 30m",
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithText("Sleep in 30m").assertIsDisplayed()
    }

    @Test
    fun `settings button visible when showSettingsButton is true`() {
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = false,
                    isBuffering = false,
                    showSettingsButton = true,
                    onSettingsClick = {},
                    onToggle = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun `onToggle callback fires when FAB clicked`() {
        var toggled = false
        composeRule.setContent {
            SirTheme {
                RadioUi(
                    modifier = Modifier.fillMaxSize(),
                    isConnected = true,
                    isPlaying = false,
                    isBuffering = false,
                    onToggle = { toggled = true }
                )
            }
        }
        composeRule.onNodeWithContentDescription("Play").performClick()
        assertTrue(toggled)
    }
}
