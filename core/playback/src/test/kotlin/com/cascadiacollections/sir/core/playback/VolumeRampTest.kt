package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeRampTest {

    @Test
    fun `ramps from silence to full volume over ShoutKit's 350ms`() {
        assertEquals(0f, VolumeRamp.levelAt(0), 0f)
        assertEquals(0.5f, VolumeRamp.levelAt(VolumeRamp.STEPS / 2), 0f)
        assertEquals(1f, VolumeRamp.levelAt(VolumeRamp.STEPS), 0f)
        assertEquals(VolumeRamp.DURATION_MS, VolumeRamp.stepDelayMs * VolumeRamp.STEPS)
    }

    @Test
    fun `out-of-range steps clamp`() {
        assertEquals(0f, VolumeRamp.levelAt(-3), 0f)
        assertEquals(1f, VolumeRamp.levelAt(VolumeRamp.STEPS + 5), 0f)
    }
}
