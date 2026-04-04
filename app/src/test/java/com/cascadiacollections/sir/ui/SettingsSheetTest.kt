package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cascadiacollections.sir.CastFeatureManager
import com.cascadiacollections.sir.CastModuleState
import com.cascadiacollections.sir.SettingsRepository
import com.cascadiacollections.sir.ui.theme.SirTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [SettingsSheet].
 * Validates that settings controls render correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createSettingsRepo() = SettingsRepository(RuntimeEnvironment.getApplication())

    private fun createMockCastManager(state: CastModuleState = CastModuleState.NotInstalled): CastFeatureManager =
        mockk(relaxed = true) {
            every { moduleState } returns MutableStateFlow(state)
        }

    @Test
    fun `settings sheet displays title`() {
        composeRule.setContent {
            SirTheme {
                SettingsSheet(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(),
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `settings sheet displays sleep timer section`() {
        composeRule.setContent {
            SirTheme {
                SettingsSheet(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(),
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Sleep Timer").assertIsDisplayed()
    }

    @Test
    fun `settings sheet displays equalizer section`() {
        composeRule.setContent {
            SirTheme {
                SettingsSheet(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(),
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Equalizer").assertIsDisplayed()
    }

    @Test
    fun `settings sheet displays Chromecast toggle`() {
        composeRule.setContent {
            SirTheme {
                SettingsSheet(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(),
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Enable Chromecast").assertIsDisplayed()
    }

    @Test
    fun `settings sheet displays privacy policy link`() {
        composeRule.setContent {
            SirTheme {
                SettingsSheet(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(),
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Privacy Policy").assertIsDisplayed()
    }

    @Test
    fun `settings sheet renders Chromecast section with NotInstalled state`() {
        composeRule.setContent {
            SirTheme {
                SettingsSheet(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(CastModuleState.NotInstalled),
                    onDismiss = {}
                )
            }
        }
        // Chromecast toggle should be interactive when not installed
        composeRule.onNodeWithText("Enable Chromecast").assertIsDisplayed()
    }
}
