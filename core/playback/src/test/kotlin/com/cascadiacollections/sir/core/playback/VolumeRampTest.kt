package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import org.junit.Test

class VolumeRampTest {

    @Test
    fun `ramps from silence to full volume over ShoutKit's 350ms`() {
        assertThat(VolumeRamp.levelAt(0)).isCloseTo(0f, 0f)
        assertThat(VolumeRamp.levelAt(VolumeRamp.STEPS / 2)).isCloseTo(0.5f, 0f)
        assertThat(VolumeRamp.levelAt(VolumeRamp.STEPS)).isCloseTo(1f, 0f)
        assertThat(VolumeRamp.stepDelayMs * VolumeRamp.STEPS).isEqualTo(VolumeRamp.DURATION_MS)
    }

    @Test
    fun `out-of-range steps clamp`() {
        assertThat(VolumeRamp.levelAt(-3)).isCloseTo(0f, 0f)
        assertThat(VolumeRamp.levelAt(VolumeRamp.STEPS + 5)).isCloseTo(1f, 0f)
    }
}
