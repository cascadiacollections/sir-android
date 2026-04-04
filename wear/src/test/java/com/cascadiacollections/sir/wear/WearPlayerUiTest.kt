package com.cascadiacollections.sir.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearPlayerUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        isPlaying: Boolean = false,
        isBuffering: Boolean = false,
        trackTitle: String? = null,
        onToggle: () -> Unit = {}
    ) {
        composeRule.setContent {
            androidx.wear.compose.material3.MaterialTheme {
                WearPlayerUi(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    trackTitle = trackTitle,
                    onToggle = onToggle
                )
            }
        }
    }

    @Test
    fun `idle state shows station name`() {
        setContent()
        composeRule.onNodeWithText("SIR").assertIsDisplayed()
    }

    @Test
    fun `idle state shows play button`() {
        setContent()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun `playing state shows pause button`() {
        setContent(isPlaying = true)
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `playing state shows live label`() {
        setContent(isPlaying = true)
        composeRule.onNodeWithText("Live").assertIsDisplayed()
    }

    @Test
    fun `track title displayed when present`() {
        setContent(trackTitle = "Test Song - Test Artist")
        composeRule.onNodeWithText("Test Song - Test Artist").assertIsDisplayed()
    }

    @Test
    fun `toggle callback fires on button click`() {
        val clicked = AtomicBoolean(false)
        setContent(onToggle = { clicked.set(true) })
        composeRule.onNodeWithContentDescription("Play").performClick()
        assertTrue(clicked.get())
    }
}
