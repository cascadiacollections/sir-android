package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.persistence.HeardTrack
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentlyHeardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `an empty history shows the empty state and no clear action`() {
        composeRule.setContent {
            SirTheme { RecentlyHeardScreen(tracks = emptyList(), onBack = {}, onClear = {}) }
        }

        composeRule.onNodeWithText("No Tracks Yet").assertIsDisplayed()
        composeRule.onNodeWithText("Clear").assertDoesNotExist()
    }

    @Test
    fun `rows show title, artist, station and relative time, and clear is offered`() {
        var cleared = false
        val now = 10L * 60 * 60 * 1000
        composeRule.setContent {
            SirTheme {
                RecentlyHeardScreen(
                    tracks = listOf(
                        HeardTrack("Song A", "Artist A", "s1", "KEXP", timestampMillis = now - 5 * 60 * 1000)
                    ),
                    onBack = {},
                    onClear = { cleared = true },
                    nowMillis = now
                )
            }
        }

        composeRule.onNodeWithText("Song A").assertIsDisplayed()
        composeRule.onNodeWithText("Artist A • KEXP").assertIsDisplayed()
        composeRule.onNodeWithText("5 minutes ago").assertIsDisplayed()
        composeRule.onNodeWithText("Clear").performClick()
        assertThat(cleared).isTrue()
    }
}
