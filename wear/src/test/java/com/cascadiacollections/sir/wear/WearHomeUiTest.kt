package com.cascadiacollections.sir.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearHomeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val default = WearStations.default("SIR")
    private val kexp = Station(id = "kexp", name = "KEXP", url = "https://kexp.example/s")

    private fun setContent(
        isPlaying: Boolean = false,
        stations: List<Station> = listOf(default, kexp),
        currentStationId: String? = null,
        onStop: () -> Unit = {},
        onPlayStation: (Station) -> Unit = {}
    ) {
        composeRule.setContent {
            androidx.wear.compose.material3.MaterialTheme {
                WearHomeUi(
                    isPlaying = isPlaying,
                    isBuffering = false,
                    stationName = null,
                    trackTitle = null,
                    stations = stations,
                    currentStationId = currentStationId,
                    onToggle = {},
                    onStop = onStop,
                    onPlayStation = onPlayStation
                )
            }
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `lists recent stations under a header`() {
        setContent()
        scrollTo("Recent stations")
        composeRule.onNodeWithText("Recent stations").assertIsDisplayed()
        scrollTo("KEXP")
        composeRule.onNodeWithText("KEXP").assertIsDisplayed()
    }

    @Test
    fun `tapping a station plays it`() {
        val played = mutableListOf<Station>()
        setContent(onPlayStation = { played += it })
        scrollTo("KEXP")
        composeRule.onNodeWithText("KEXP").performClick()
        assertEquals(listOf(kexp), played)
    }

    @Test
    fun `current station is marked now playing`() {
        setContent(isPlaying = true, currentStationId = "kexp")
        scrollTo("Now playing")
        composeRule.onNodeWithText("Now playing").assertIsDisplayed()
    }

    @Test
    fun `stop is offered only while playing`() {
        setContent(isPlaying = false)
        assertEquals(0, composeRule.onAllNodes(hasText("Stop")).fetchSemanticsNodes().size)
    }

    @Test
    fun `stop callback fires`() {
        var stopped = false
        setContent(isPlaying = true, onStop = { stopped = true })
        scrollTo("Stop")
        composeRule.onNodeWithText("Stop").performClick()
        assertTrue(stopped)
    }
}
