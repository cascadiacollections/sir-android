package com.cascadiacollections.sir.core.persistence

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.QuickPlaySelection.TapAction
import org.junit.Test

class QuickPlaySelectionTest {

    private fun station(id: String, name: String = "Station $id") =
        Station(id = id, name = name, url = "https://example.com/$id")

    private val a = station("a")
    private val b = station("b")

    @Test
    fun `defaults to the first saved station when nothing is pinned`() {
        assertThat(QuickPlaySelection.resolveStation(listOf(a, b), pinnedId = null)).isEqualTo(a)
    }

    @Test
    fun `uses the pinned station when it is still saved`() {
        assertThat(QuickPlaySelection.resolveStation(listOf(a, b), pinnedId = "b")).isEqualTo(b)
    }

    @Test
    fun `falls back to the first saved station once the pinned one is unsaved`() {
        assertThat(QuickPlaySelection.resolveStation(listOf(a), pinnedId = "b")).isEqualTo(a)
    }

    @Test
    fun `resolves to null without favourites`() {
        assertThat(QuickPlaySelection.resolveStation(emptyList(), pinnedId = "b")).isNull()
    }

    @Test
    fun `tapping a station that is not current selects and plays it`() {
        assertThat(QuickPlaySelection.tapAction(b, selected = a, isPlaying = true))
            .isEqualTo(TapAction.SelectAndPlay(b))
        assertThat(QuickPlaySelection.tapAction(b, selected = null, isPlaying = false))
            .isEqualTo(TapAction.SelectAndPlay(b))
    }

    @Test
    fun `tapping the current station toggles playback`() {
        assertThat(QuickPlaySelection.tapAction(a, selected = a, isPlaying = true)).isEqualTo(TapAction.Pause)
        assertThat(QuickPlaySelection.tapAction(a, selected = a, isPlaying = false)).isEqualTo(TapAction.Play)
    }

    @Test
    fun `without favourites the button toggles whatever is selected`() {
        assertThat(QuickPlaySelection.tapAction(null, selected = null, isPlaying = true)).isEqualTo(TapAction.Pause)
        assertThat(QuickPlaySelection.tapAction(null, selected = a, isPlaying = false)).isEqualTo(TapAction.Play)
    }

    @Test
    fun `pause is shown only while the widget's own station plays`() {
        assertThat(QuickPlaySelection.showsPause(a, selected = a, isPlaying = true)).isTrue()
        assertThat(QuickPlaySelection.showsPause(a, selected = b, isPlaying = true)).isFalse()
        assertThat(QuickPlaySelection.showsPause(a, selected = a, isPlaying = false)).isFalse()
        assertThat(QuickPlaySelection.showsPause(null, selected = b, isPlaying = true)).isTrue()
    }

    @Test
    fun `initials take the first letter of up to two words`() {
        assertThat(QuickPlaySelection.initials("Radio Paradise")).isEqualTo("RP")
        assertThat(QuickPlaySelection.initials("soma_fm groove salad")).isEqualTo("SF")
        assertThat(QuickPlaySelection.initials("KEXP")).isEqualTo("K")
        assertThat(QuickPlaySelection.initials("  ")).isEmpty()
    }
}
