package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioUiStateTest {

    @Test
    fun `default RadioUiState has expected initial values`() {
        val state = RadioUiState()
        assertFalse(state.isConnected)
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertFalse(state.isError)
        assertNull(state.trackTitle)
        assertNull(state.artist)
        assertNull(state.sleepTimerLabel)
        assertFalse(state.showMeteredWarning)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val original = RadioUiState(
            isConnected = true,
            isPlaying = true,
            trackTitle = "Song",
            artist = "Artist"
        )
        val copied = original.copy(isPlaying = false)
        assertTrue(copied.isConnected)
        assertFalse(copied.isPlaying)
        assertEquals("Song", copied.trackTitle)
        assertEquals("Artist", copied.artist)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = RadioUiState(isPlaying = true, trackTitle = "Song")
        val b = RadioUiState(isPlaying = true, trackTitle = "Song")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `destructuring works for all properties`() {
        val state = RadioUiState(
            isConnected = true,
            isPlaying = true,
            isBuffering = false,
            isError = false,
            trackTitle = "Title",
            artist = "Artist",
            sleepTimerLabel = "30m",
            showMeteredWarning = true
        )
        val (connected, playing, buffering, error, title, artist, timer, metered) = state
        assertTrue(connected)
        assertTrue(playing)
        assertFalse(buffering)
        assertFalse(error)
        assertEquals("Title", title)
        assertEquals("Artist", artist)
        assertEquals("30m", timer)
        assertTrue(metered)
    }
}
