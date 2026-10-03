package com.cascadiacollections.sir.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.cascadiacollections.sir.SearchUiState
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The browse tab's idle Listen Now page: Recently Played shelf, genres, Popular grid. */
@RunWith(RobolectricTestRunner::class)
// A phone-sized screen, so the grid lays out two columns as on a device.
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ListenNowContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val played = mutableListOf<String>()
    private val hidden = mutableListOf<String>()
    private val undone = mutableListOf<String>()
    private val toggled = mutableListOf<Pair<String, Boolean>>()
    private var retriedPopular = false

    private fun station(id: String, name: String = "Station $id") = Station(id = id, name = name, url = "https://$id")

    private fun setContent(state: SearchUiState, saved: Set<String> = emptySet()) {
        composeRule.setContent {
            SirTheme {
                BrowseContent(
                    // One genre keeps the grid on the test's small screen.
                    state = state.copy(genres = listOf(Tag("jazz"))),
                    savedStationIds = saved,
                    selectedStationId = null,
                    onQueryChange = {},
                    onSubmit = {},
                    onSelectGenre = {},
                    onFiltersChange = {},
                    onRetry = {},
                    onPlay = { played += it.id },
                    onToggleSaved = { s, isSaved -> toggled += s.id to isSaved },
                    onRetryPopular = { retriedPopular = true },
                    onHideRecent = { hidden += it.id },
                    onUndoHideRecent = { undone += it.id }
                )
            }
        }
    }

    @Test
    fun `the shelf, genres and titled grid appear in order`() {
        setContent(
            SearchUiState(
                recentShelf = listOf(station("r", "Recent One")),
                popularStations = listOf(station("p", "Pop One"))
            )
        )

        val shelfTop = composeRule.onNodeWithText("Recently Played").fetchSemanticsNode().boundsInRoot.top
        val genresTop = composeRule.onNodeWithText("Browse by Genre").fetchSemanticsNode().boundsInRoot.top
        val popularTop = composeRule.onNodeWithText("Popular stations").fetchSemanticsNode().boundsInRoot.top
        assertTrue(shelfTop < genresTop && genresTop < popularTop)
        composeRule.onNodeWithText("Pop One").assertIsDisplayed()
    }

    @Test
    fun `tapping a tile plays it`() {
        setContent(SearchUiState(recentShelf = listOf(station("r")), popularStations = listOf(station("p"))))

        composeRule.onNodeWithTag(ListenNowTestTags.recentTile("r")).performClick()
        composeRule.onNodeWithTag(ListenNowTestTags.popularTile("p")).performClick()

        assertEquals(listOf("r", "p"), played)
    }

    @Test
    fun `a tile is one node named after its station`() {
        setContent(SearchUiState(popularStations = listOf(station("p", "Jazz FM"))))

        composeRule.onNodeWithTag(ListenNowTestTags.popularTile("p")).assert(hasText("Jazz FM"))
        // Merged: the name is not a separately focusable node.
        composeRule.onAllNodesWithText("Jazz FM").assertCountEquals(1)
    }

    @Test
    fun `hiding from the shelf offers undo`() {
        setContent(SearchUiState(recentShelf = listOf(station("r"))))

        composeRule.onNodeWithTag(ListenNowTestTags.recentTile("r")).performTouchInput { longClick() }
        composeRule.onNodeWithText("Hide from Recently Played").performClick()
        assertEquals(listOf("r"), hidden)

        composeRule.onNodeWithText("Removed").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("r"), undone)
    }

    @Test
    fun `long-press actions are also accessibility actions`() {
        setContent(SearchUiState(recentShelf = listOf(station("r")), popularStations = listOf(station("p"))))

        fun actionsOf(tag: String) =
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions]

        val hide = actionsOf(ListenNowTestTags.recentTile("r")).single()
        assertEquals("Hide from Recently Played", hide.label)
        val save = actionsOf(ListenNowTestTags.popularTile("p")).single()
        assertEquals("Add to My Stations", save.label)

        composeRule.runOnIdle {
            hide.action()
            save.action()
        }
        assertEquals(listOf("r"), hidden)
        assertEquals(listOf("p" to false), toggled)
    }

    @Test
    fun `the grid tile menu saves and unsaves`() {
        setContent(SearchUiState(popularStations = listOf(station("a"), station("b"))), saved = setOf("b"))

        composeRule.onNodeWithTag(ListenNowTestTags.popularTile("a")).performTouchInput { longClick() }
        composeRule.onNodeWithText("Add to My Stations").performClick()
        composeRule.onNodeWithTag(ListenNowTestTags.popularTile("b")).performTouchInput { longClick() }
        composeRule.onNodeWithText("Remove from My Stations").performClick()

        assertEquals(listOf("a" to false, "b" to true), toggled)
    }

    @Test
    fun `loading says tuning in`() {
        setContent(SearchUiState(isLoadingPopular = true))

        composeRule.onNodeWithText("Tuning in…").assertIsDisplayed()
    }

    @Test
    fun `a failed load with nothing to show offers try again`() {
        setContent(SearchUiState(popularLoadFailed = true))

        composeRule.onNodeWithText("Directory unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertTrue(retriedPopular)
    }

    @Test
    fun `a failed refresh over shown stations keeps them with a notice`() {
        setContent(SearchUiState(popularLoadFailed = true, popularStations = listOf(station("p", "Kept"))))

        composeRule.onNodeWithText("Showing saved stations").assertIsDisplayed()
        composeRule.onNodeWithText("Kept").assertIsDisplayed()
        composeRule.onNodeWithText("Directory unavailable").assertDoesNotExist()
    }
}
