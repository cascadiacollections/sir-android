package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunWelcomeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `welcome names the app and dismisses on get started`() {
        var done = 0
        composeRule.setContent {
            SirTheme { FirstRunWelcome(onDone = { done++ }) }
        }

        composeRule.onNodeWithText("Welcome to SIR").assertIsDisplayed()
        composeRule.onNodeWithText("No accounts, no tracking. Find a station and press play.").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").performClick()

        assertThat(done).isEqualTo(1)
    }
}
