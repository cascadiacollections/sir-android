package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PausedReleaseTest {

    private var now = 1_000L
    private val release = PausedRelease(clock = { now })

    @Test
    fun `the default is ten minutes`() {
        assertEquals(600_000L, PausedRelease.DEFAULT_TIMEOUT_MS)
        assertEquals(600_000L, release.onPaused())
    }

    @Test
    fun `nothing is due before ten minutes paused`() {
        release.onPaused()
        now += 599_999L

        assertFalse(release.isDue())
    }

    @Test
    fun `ten minutes paused is due`() {
        release.onPaused()
        now += 600_000L

        assertTrue(release.isDue())
    }

    @Test
    fun `playing cancels the release`() {
        release.onPaused()
        now += 300_000L
        release.onPlay()
        now += 600_000L

        assertFalse(release.isDue())
        assertFalse(release.isArmed)
    }

    @Test
    fun `a pause after playing again starts a fresh ten minutes`() {
        release.onPaused()
        now += 500_000L
        release.onPlay()
        now += 10_000L

        assertEquals(600_000L, release.onPaused())
        now += 500_000L
        assertFalse(release.isDue())
    }

    @Test
    fun `pausing again keeps the original start`() {
        release.onPaused()
        now += 400_000L

        assertEquals(200_000L, release.onPaused())
        now += 200_000L
        assertTrue(release.isDue())
    }

    @Test
    fun `a check that fires late still reports due once, until the next pause`() {
        release.onPaused()
        now += 3_600_000L
        assertTrue(release.isDue())

        release.onReleased()

        assertFalse(release.isDue())
        assertFalse(release.isArmed)
    }

    @Test
    fun `nothing is due without a pause`() {
        now += 3_600_000L

        assertFalse(release.isDue())
    }
}
