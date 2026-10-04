package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android TV home: banner, shelves and their load states. */
@RunWith(RobolectricTestRunner::class)
// Taller than a real TV (960x540dp) so both shelves are composed without scrolling.
@Config(sdk = [34], qualifiers = "w960dp-h1400dp-television")
class TvHomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selected = mutableListOf<String>()
    private var toggles = 0
    private var stops = 0
    private var retries = 0

    private fun station(id: String, name: String = "Station $id", tags: String = "") =
        Station(id = id, name = name, url = "https://$id", tags = tags)

    private fun setContent(
        radio: RadioUiState = RadioUiState(isConnected = true),
        recents: List<Station> = emptyList(),
        popular: TvPopular = TvPopular.Loaded(emptyList())
    ) {
        composeRule.setContent {
            SirTheme(darkTheme = true) {
                TvHomeScreen(
                    radio = radio,
                    recents = recents,
                    popular = popular,
                    onStationSelected = { selected += it.id },
                    onTogglePlayback = { toggles++ },
                    onStop = { stops++ },
                    onRetryPopular = { retries++ }
                )
            }
        }
    }

    @Test
    fun `the banner names the current station and its genre`() {
        setContent(radio = RadioUiState(isConnected = true, station = station("k", "KEXP", tags = "indie,rock")))

        composeRule.onNodeWithText("KEXP").assertIsDisplayed()
        composeRule.onNodeWithText("Indie").assertIsDisplayed()
    }

    @Test
    fun `play and stop reach their callbacks`() {
        setContent()

        composeRule.onNodeWithText("Play").performClick()
        composeRule.onNodeWithText("Stop").performClick()

        assertEquals(1, toggles)
        assertEquals(1, stops)
    }

    @Test
    fun `choosing a card selects that station`() {
        setContent(
            recents = listOf(station("r", "Recent One")),
            popular = TvPopular.Loaded(listOf(station("p", "Pop One")))
        )

        composeRule.onNodeWithText("Recent One").performClick()
        composeRule.onNodeWithText("Pop One").performClick()

        assertEquals(listOf("r", "p"), selected)
    }

    @Test
    fun `no recents hides the shelf`() {
        setContent(popular = TvPopular.Loaded(listOf(station("p"))))

        assertTrue(composeRule.onAllNodesWithTextCount("Recently Played") == 0)
    }

    @Test
    fun `a failed popular load offers retry`() {
        setContent(popular = TvPopular.Failed)

        composeRule.onNodeWithText("Couldn't load stations. Check the connection.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `loading and empty states are explained`() {
        setContent(popular = TvPopular.Loading)
        composeRule.onNodeWithText("Loading stations…").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
