package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cascadiacollections.sir.BuildConfig
import com.cascadiacollections.sir.CastFeatureManager
import com.cascadiacollections.sir.CastModuleState
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.ui.theme.SirTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
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
    fun `settings screen displays equalizer band sliders`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager()
                )
            }
        }
        composeRule.onNodeWithText("Bass").assertIsDisplayed()
        composeRule.onNodeWithText("Treble").assertIsDisplayed()
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
        composeRule.onNodeWithText("Enable Chromecast").performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithText("Privacy Policy").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen displays the report plays toggle under privacy`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager()
                )
            }
        }
        composeRule.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Report plays to Radio Browser").performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithText("Enable Chromecast").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen omits the Chromecast section when casting is unavailable`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(CastModuleState.Unavailable)
                )
            }
        }
        // The FOSS build cannot install the module, so the row is absent rather than
        // present-but-disabled. The rest of the screen still renders.
        composeRule.onNodeWithText("Enable Chromecast").assertDoesNotExist()
        composeRule.onNodeWithText("Privacy Policy").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen shows the prewarming toggle under playback`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager()
                )
            }
        }
        composeRule.onNodeWithText("Playback").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Pre-connect to favorite stations").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen ends with an about section`() {
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager()
                )
            }
        }
        composeRule.onNodeWithText("About").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Version").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_COMMIT}"
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Source code").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Report an issue").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Station data from Radio Browser").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open Source Licenses").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `about links open the licenses screen`() {
        var opened = false
        composeRule.setContent {
            SirTheme {
                SettingsContent(
                    settingsRepository = createSettingsRepo(),
                    castFeatureManager = createMockCastManager(),
                    onOpenLicenses = { opened = true }
                )
            }
        }
        composeRule.onNodeWithText("Open Source Licenses").performScrollTo().performClick()
        assertTrue(opened)
    }
}
