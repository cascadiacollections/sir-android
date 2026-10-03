package com.cascadiacollections.sir.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cascadiacollections.sir.SearchPhase
import com.cascadiacollections.sir.SearchUiState
import com.cascadiacollections.sir.core.directory.StationSearchFilters
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowseContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selectedGenre: Tag? = null
    private var appliedFilters: StationSearchFilters? = null
    private var retried = false

    private fun setContent(state: SearchUiState) {
        composeRule.setContent {
            SirTheme {
                BrowseContent(
                    state = state,
                    savedStationIds = emptySet(),
                    selectedStationId = null,
                    onQueryChange = {},
                    onSubmit = {},
                    onSelectGenre = { selectedGenre = it },
                    onFiltersChange = { appliedFilters = it },
                    onRetry = { retried = true },
                    onPlay = {},
                    onToggleSaved = { _, _ -> }
                )
            }
        }
    }

    @Test
    fun `idle shows genre chips and popular stations`() {
        setContent(
            SearchUiState(popularStations = listOf(Station(id = "a", name = "Station A", url = "https://a")))
        )

        composeRule.onNodeWithText("Browse by Genre").assertIsDisplayed()
        composeRule.onNodeWithText("Popular stations").assertIsDisplayed()
        composeRule.onNodeWithText("Station A").assertIsDisplayed()
        composeRule.onNodeWithText("Hip Hop").assertIsNotSelected().performClick()
        assertEquals(Tag("hip hop"), selectedGenre)
    }

    @Test
    fun `the browsed genre's chip is selected`() {
        setContent(
            SearchUiState(
                query = "Jazz",
                selectedGenre = Tag("jazz"),
                phase = SearchPhase.Searching
            )
        )

        // "Jazz" is also the field text; only the chip carries a selected state.
        composeRule.onNode(hasText("Jazz") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .assertIsSelected()
        composeRule.onNodeWithText("Rock").assertIsNotSelected()
    }

    @Test
    fun `the filter button announces active filters`() {
        setContent(SearchUiState(filters = StationSearchFilters(countryCode = "DE")))

        composeRule.onNodeWithContentDescription("Filters active").assertIsDisplayed()
    }

    @Test
    fun `the filter sheet applies the edited filters on done`() {
        setContent(SearchUiState())

        composeRule.onNodeWithContentDescription("Filters").performClick()
        composeRule.onNodeWithTag(BrowseScreenTestTags.FILTER_MIN_BITRATE).performClick()
        composeRule.onNodeWithText("128 kbps").performClick()
        composeRule.onNodeWithTag(BrowseScreenTestTags.FILTER_COUNTRY_FIELD).performTextInput("us1a")
        composeRule.onNodeWithTag(BrowseScreenTestTags.FILTER_TAG_FIELD).performTextInput("jazz")
        composeRule.onNodeWithTag(BrowseScreenTestTags.FILTER_DONE).performClick()

        assertEquals(
            StationSearchFilters(bitrateMinKbps = 128, tag = "jazz", countryCode = "US"),
            appliedFilters
        )
    }

    @Test
    fun `an empty result with filters offers to clear them`() {
        setContent(
            SearchUiState(
                query = "x",
                phase = SearchPhase.Empty,
                filters = StationSearchFilters(bitrateMinKbps = 320)
            )
        )

        composeRule.onNodeWithText("No matching stations").assertIsDisplayed()
        composeRule.onNodeWithText("Filters: min 320 kbps").assertIsDisplayed()
        composeRule.onNodeWithText("Clear filters").performClick()
        assertEquals(StationSearchFilters.NONE, appliedFilters)
    }

    @Test
    fun `a failed search offers retry`() {
        setContent(SearchUiState(query = "x", phase = SearchPhase.Failed))

        composeRule.onNodeWithText("Search unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun `row subtitle is the first genre and bitrate`() {
        val kbps = { n: Int -> "$n kbps" }
        assertEquals("Hip Hop · 128 kbps", Station(tags = "hip hop,rap", bitrate = 128).browseSubtitle(kbps))
        assertEquals("Jazz", Station(tags = "jazz", bitrate = 0).browseSubtitle(kbps))
        assertEquals("64 kbps", Station(bitrate = 64).browseSubtitle(kbps))
        assertEquals("", Station().browseSubtitle(kbps))
    }

    @Test
    fun `country codes keep two letters, upper-cased`() {
        assertEquals("US", sanitizeCountryCode("u1s-a"))
        assertEquals("", sanitizeCountryCode("12"))
    }
}
