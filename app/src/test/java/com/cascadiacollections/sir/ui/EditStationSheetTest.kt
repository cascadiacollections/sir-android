package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditStationSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val station = Station(
        id = "a",
        name = "Station A",
        url = "https://example.com/a",
        favicon = "https://example.com/a.png"
    )

    @Test
    fun `sheet is pre-filled with the station's current values`() {
        composeRule.setContent {
            SirTheme { EditStationSheet(station = station, onDismiss = {}, onSave = {}) }
        }

        composeRule.onNodeWithTag(EditStationSheetTestTags.NAME_FIELD).assertTextEquals("Name", "Station A")
        composeRule.onNodeWithTag(EditStationSheetTestTags.URL_FIELD)
            .assertTextEquals("Stream URL", "https://example.com/a")
        composeRule.onNodeWithTag(EditStationSheetTestTags.ARTWORK_FIELD)
            .assertTextEquals("Artwork URL (optional)", "https://example.com/a.png")
    }

    @Test
    fun `save is disabled once the name is cleared`() {
        composeRule.setContent {
            SirTheme { EditStationSheet(station = station, onDismiss = {}, onSave = {}) }
        }

        composeRule.onNodeWithTag(EditStationSheetTestTags.NAME_FIELD).performTextClearance()

        composeRule.onNodeWithTag(EditStationSheetTestTags.SAVE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `save is disabled for a URL without a scheme`() {
        composeRule.setContent {
            SirTheme { EditStationSheet(station = station, onDismiss = {}, onSave = {}) }
        }

        composeRule.onNodeWithTag(EditStationSheetTestTags.URL_FIELD).performTextClearance()
        composeRule.onNodeWithTag(EditStationSheetTestTags.URL_FIELD).performTextInput("example.com/a")

        composeRule.onNodeWithTag(EditStationSheetTestTags.SAVE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `saving reports the edited station, trimmed, with the same id`() {
        var saved: Station? = null
        composeRule.setContent {
            SirTheme {
                EditStationSheet(
                    station = station,
                    onDismiss = {},
                    onSave = { saved = it }
                )
            }
        }

        composeRule.onNodeWithTag(EditStationSheetTestTags.NAME_FIELD).performTextClearance()
        composeRule.onNodeWithTag(EditStationSheetTestTags.NAME_FIELD).performTextInput(" Renamed Station ")
        composeRule.onNodeWithTag(EditStationSheetTestTags.SAVE_BUTTON).performClick()

        assertEquals("Renamed Station", saved?.name)
        assertEquals(station.id, saved?.id)
    }

    @Test
    fun `clearing the artwork field saves a null favicon`() {
        var saved: Station? = null
        composeRule.setContent {
            SirTheme {
                EditStationSheet(
                    station = station,
                    onDismiss = {},
                    onSave = { saved = it }
                )
            }
        }

        composeRule.onNodeWithTag(EditStationSheetTestTags.ARTWORK_FIELD).performTextClearance()
        composeRule.onNodeWithTag(EditStationSheetTestTags.SAVE_BUTTON).performClick()

        assertNull(saved?.favicon)
    }
}
