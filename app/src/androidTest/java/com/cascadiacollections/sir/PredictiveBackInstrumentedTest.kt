package com.cascadiacollections.sir

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Instrumented, on-device verification that the system back gesture dismisses the Settings
 * overlay — the real-device counterpart to the Robolectric-level dismissal assertions in
 * [com.cascadiacollections.sir.ui.SettingsSheetTest]. Robolectric can't exercise the actual
 * [androidx.activity.OnBackPressedDispatcher]/predictive-back callback registration end to end,
 * so this drives it via [MainActivity]'s real dispatcher instead of simulating a raw touch swipe
 * (the swipe animation itself is a system-rendering concern, not app logic under test).
 */
@RunWith(AndroidJUnit4::class)
class PredictiveBackInstrumentedTest {

    // Pre-grant the runtime permissions RadioScreen requests on launch (POST_NOTIFICATIONS,
    // BLUETOOTH_CONNECT) so the system permission dialog never covers the Compose content —
    // a fresh install otherwise races the dialog against this test's node lookups.
    private val runtimePermissions: List<String> = listOfNotNull(
        Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
        Manifest.permission.BLUETOOTH_CONNECT.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    )
    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*runtimePermissions.toTypedArray())
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Test
    fun systemBackDismissesSettingsSheet() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings").assertExists()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sleep Timer").assertDoesNotExist()
    }
}
