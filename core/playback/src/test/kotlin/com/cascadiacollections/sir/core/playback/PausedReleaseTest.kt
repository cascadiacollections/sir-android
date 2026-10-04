package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class PausedReleaseTest {

    private var now = 1_000L
    private val release = PausedRelease(clock = { now })

    @Test
    fun `the default is ten minutes`() {
        assertThat(PausedRelease.DEFAULT_TIMEOUT_MS).isEqualTo(600_000L)
        assertThat(release.onPaused()).isEqualTo(600_000L)
    }

    @Test
    fun `nothing is due before ten minutes paused`() {
        release.onPaused()
        now += 599_999L

        assertThat(release.isDue()).isFalse()
    }

    @Test
    fun `ten minutes paused is due`() {
        release.onPaused()
        now += 600_000L

        assertThat(release.isDue()).isTrue()
    }

    @Test
    fun `playing cancels the release`() {
        release.onPaused()
        now += 300_000L
        release.onPlay()
        now += 600_000L

        assertThat(release.isDue()).isFalse()
        assertThat(release.isArmed).isFalse()
    }

    @Test
    fun `a pause after playing again starts a fresh ten minutes`() {
        release.onPaused()
        now += 500_000L
        release.onPlay()
        now += 10_000L

        assertThat(release.onPaused()).isEqualTo(600_000L)
        now += 500_000L
        assertThat(release.isDue()).isFalse()
    }

    @Test
    fun `pausing again keeps the original start`() {
        release.onPaused()
        now += 400_000L

        assertThat(release.onPaused()).isEqualTo(200_000L)
        now += 200_000L
        assertThat(release.isDue()).isTrue()
    }

    @Test
    fun `a check that fires late still reports due once, until the next pause`() {
        release.onPaused()
        now += 3_600_000L
        assertThat(release.isDue()).isTrue()

        release.onReleased()

        assertThat(release.isDue()).isFalse()
        assertThat(release.isArmed).isFalse()
    }

    @Test
    fun `nothing is due without a pause`() {
        now += 3_600_000L

        assertThat(release.isDue()).isFalse()
    }
}
