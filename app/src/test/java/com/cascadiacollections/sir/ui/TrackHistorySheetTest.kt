package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cascadiacollections.sir.core.playback.TrackHistoryEntry
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackHistorySheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `an empty history shows the empty-state message`() {
        composeRule.setContent {
            SirTheme { TrackHistorySheet(history = emptyList(), onDismiss = {}) }
        }

        composeRule.onNodeWithText("Tracks you've heard this session will show up here.")
            .assertIsDisplayed()
    }

    @Test
    fun `history entries display their title and artist`() {
        composeRule.setContent {
            SirTheme {
                TrackHistorySheet(
                    history = listOf(
                        TrackHistoryEntry(title = "Song A", artist = "Artist A", timestampMillis = 1L)
                    ),
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Song A").assertIsDisplayed()
    }
}
