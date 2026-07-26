package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cascadiacollections.sir.CastFeatureManager
import com.cascadiacollections.sir.CastModuleState
import com.cascadiacollections.sir.core.persistence.SettingsRepository
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
 * Compose UI tests for [SettingsContent].
 * Validates that settings controls render correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createSettingsRepo() = SettingsRepository(RuntimeEnvironment.getApplication())

    private fun createMockCastManager(state: CastModuleState = CastModuleState.NotInstalled): CastFeatureManager =
        mockk(relaxed = true) {
            every { moduleState } returns MutableStateFlow(state)
        }

    @Test
    fun `settings screen displays sleep timer section`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager()
                )
            }
        }
        composeRule.onNodeWithText("Sleep Timer").assertIsDisplayed()
    }

    @Test
    fun `settings screen displays equalizer section`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager()
                )
            }
        }
        composeRule.onNodeWithText("Equalizer").assertIsDisplayed()
    }

    @Test
    fun `settings screen displays Chromecast toggle`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager()
                )
            }
        }
        composeRule.onNodeWithText("Enable Chromecast").assertIsDisplayed()
    }

    @Test
    fun `settings screen displays privacy policy link`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager()
                )
            }
        }
        composeRule.onNodeWithText("Privacy Policy").assertIsDisplayed()
    }

        @Test
    fun `settings screen renders Chromecast section with NotInstalled state`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(CastModuleState.NotInstalled)
                )
            }
        }
        // Chromecast toggle should be interactive when not installed
        composeRule.onNodeWithText("Enable Chromecast").assertIsDisplayed()
    }
}
