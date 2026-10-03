package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.QuickPlaySelection.TapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPlaySelectionTest {

    private fun station(id: String, name: String = "Station $id") =
        Station(id = id, name = name, url = "https://example.com/$id")

    private val a = station("a")
    private val b = station("b")

    @Test
    fun `defaults to the first saved station when nothing is pinned`() {
        assertEquals(a, QuickPlaySelection.resolveStation(listOf(a, b), pinnedId = null))
    }

    @Test
    fun `uses the pinned station when it is still saved`() {
        assertEquals(b, QuickPlaySelection.resolveStation(listOf(a, b), pinnedId = "b"))
    }

    @Test
    fun `falls back to the first saved station once the pinned one is unsaved`() {
        assertEquals(a, QuickPlaySelection.resolveStation(listOf(a), pinnedId = "b"))
    }

    @Test
    fun `resolves to null without favourites`() {
        assertNull(QuickPlaySelection.resolveStation(emptyList(), pinnedId = "b"))
    }

    @Test
    fun `tapping a station that is not current selects and plays it`() {
        assertEquals(TapAction.SelectAndPlay(b), QuickPlaySelection.tapAction(b, selected = a, isPlaying = true))
        assertEquals(TapAction.SelectAndPlay(b), QuickPlaySelection.tapAction(b, selected = null, isPlaying = false))
    }

    @Test
    fun `tapping the current station toggles playback`() {
        assertEquals(TapAction.Pause, QuickPlaySelection.tapAction(a, selected = a, isPlaying = true))
        assertEquals(TapAction.Play, QuickPlaySelection.tapAction(a, selected = a, isPlaying = false))
    }

    @Test
    fun `without favourites the button toggles whatever is selected`() {
        assertEquals(TapAction.Pause, QuickPlaySelection.tapAction(null, selected = null, isPlaying = true))
        assertEquals(TapAction.Play, QuickPlaySelection.tapAction(null, selected = a, isPlaying = false))
    }

    @Test
    fun `pause is shown only while the widget's own station plays`() {
        assertTrue(QuickPlaySelection.showsPause(a, selected = a, isPlaying = true))
        assertFalse(QuickPlaySelection.showsPause(a, selected = b, isPlaying = true))
        assertFalse(QuickPlaySelection.showsPause(a, selected = a, isPlaying = false))
        assertTrue(QuickPlaySelection.showsPause(null, selected = b, isPlaying = true))
    }

    @Test
    fun `initials take the first letter of up to two words`() {
        assertEquals("RP", QuickPlaySelection.initials("Radio Paradise"))
        assertEquals("SF", QuickPlaySelection.initials("soma_fm groove salad"))
        assertEquals("K", QuickPlaySelection.initials("KEXP"))
        assertEquals("", QuickPlaySelection.initials("  "))
    }
}
