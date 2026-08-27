package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
    fun `a blank artwork URL is treated the same as no artwork`() {
        composeRule.setContent {
            SirTheme {
                StationRow(station = stationWithoutArtwork.copy(favicon = "   "), isPlaying = false, onPlay = {})
            }
        }

        composeRule.onNodeWithContentDescription("Play station").assertIsDisplayed()
    }
}
