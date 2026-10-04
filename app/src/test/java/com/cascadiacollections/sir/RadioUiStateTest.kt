package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class RadioUiStateTest {

    @Test
    fun `default RadioUiState has expected initial values`() {
        val state = RadioUiState()
        assertThat(state.isConnected).isFalse()
        assertThat(state.isPlaying).isFalse()
        assertThat(state.isBuffering).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.trackTitle).isNull()
        assertThat(state.artist).isNull()
        assertThat(state.sleepTimerLabel).isNull()
        assertThat(state.showMeteredWarning).isFalse()
        assertThat(state.trackHistory).isEmpty()
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
        assertThat(copied.isConnected).isTrue()
        assertThat(copied.isPlaying).isFalse()
        assertThat(copied.trackTitle).isEqualTo("Song")
        assertThat(copied.artist).isEqualTo("Artist")
    }

    @Test
    fun `data class equality works correctly`() {
        val a = RadioUiState(isPlaying = true, trackTitle = "Song")
        val b = RadioUiState(isPlaying = true, trackTitle = "Song")
        assertThat(b).isEqualTo(a)
        assertThat(b.hashCode()).isEqualTo(a.hashCode())
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
        assertThat(connected).isTrue()
        assertThat(playing).isTrue()
        assertThat(buffering).isFalse()
        assertThat(error).isFalse()
        assertThat(title).isEqualTo("Title")
        assertThat(artist).isEqualTo("Artist")
        assertThat(timer).isEqualTo("30m")
        assertThat(metered).isTrue()
    }
}
