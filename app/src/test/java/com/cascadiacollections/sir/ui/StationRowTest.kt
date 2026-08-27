package com.cascadiacollections.sir.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StationRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stationWithoutArtwork = Station(id = "a", name = "Station A", url = "https://example.com/a")
    private val stationWithArtwork = stationWithoutArtwork.copy(favicon = "https://example.com/a.png")

    @Test
    fun `a station without artwork falls back to the play icon`() {
        composeRule.setContent {
            SirTheme { StationRow(station = stationWithoutArtwork, isPlaying = false, onPlay = {}) }
        }

        composeRule.onNodeWithText("Station A").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play station").assertIsDisplayed()
    }

    @Test
    fun `a station with an artwork URL still renders without crashing`() {
        composeRule.setContent {
            SirTheme { StationRow(station = stationWithArtwork, isPlaying = false, onPlay = {}) }
        }

        composeRule.onNodeWithText("Station A").assertIsDisplayed()
    }

    @Test
    fun `a failing artwork load falls back to the play icon`() {
        // A real ImageLoader (not SirTheme's), wired to an engine that always fails, so
        // the fallback is exercised deterministically rather than depending on an
        // actual network request's timing.
        val failingEngine = FakeImageLoaderEngine.Builder()
            .default { chain -> ErrorResult(image = null, request = chain.request, throwable = RuntimeException("boom")) }
            .build()

        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).components { add(failingEngine) }.build()
            }
            MaterialTheme {
                StationRow(station = stationWithArtwork, isPlaying = false, onPlay = {})
            }
        }

        composeRule.onNodeWithContentDescription("Play station").assertIsDisplayed()
    }

    @Test
    fun `a blank artwork URL is treated the same as no artwork`() {
        composeRule.setContent {
            SirTheme {
                StationRow(station = stationWithoutArtwork.copy(favicon = "   "), isPlaying = false, onPlay = {})
            }
        }

        composeRule.onNodeWithContentDescription("Play station").assertIsDisplayed()
    }
}
