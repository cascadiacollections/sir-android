package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import com.cascadiacollections.sir.core.playback.VolumeRamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeFaderTest {

    // backgroundScope work isn't run by advanceUntilIdle, so step the virtual clock past a ramp.
    private fun TestScope.finishRamp() {
        advanceTimeBy(VolumeRamp.DURATION_MS + 1)
        runCurrent()
    }

    @Test
    fun `fades from silence to full volume over the ramp`() = runTest {
        val volumes = mutableListOf<Float>()
        val fader = VolumeFader(backgroundScope) { volumes += it }

        fader.silence()
        fader.fadeIn()
        advanceTimeBy(VolumeRamp.DURATION_MS / 2)
        runCurrent()
        val halfway = volumes.last()
        finishRamp()

        assertThat(volumes.first()).isEqualTo(0f)
        assertThat(volumes).hasSize(VolumeRamp.STEPS + 1)
        assertThat(halfway).isCloseTo(0.5f, 0.08f)
        assertThat(volumes.last()).isEqualTo(1f)
        assertThat(volumes).isEqualTo(volumes.sorted())
    }

    @Test
    fun `stopping mid-fade cancels the ramp and stays silent`() = runTest {
        val volumes = mutableListOf<Float>()
        val fader = VolumeFader(backgroundScope) { volumes += it }

        fader.fadeIn()
        advanceTimeBy(VolumeRamp.DURATION_MS / 2)
        fader.silence()
        finishRamp()

        assertThat(volumes.last()).isEqualTo(0f)
    }

    @Test
    fun `a new start restarts the ramp rather than running two`() = runTest {
        val volumes = mutableListOf<Float>()
        val fader = VolumeFader(backgroundScope) { volumes += it }

        fader.fadeIn()
        advanceTimeBy(VolumeRamp.DURATION_MS / 2)
        runCurrent()
        val before = volumes.size
        fader.fadeIn()
        finishRamp()

        // Exactly one full ramp after the restart, starting from the first step again.
        assertThat(volumes.size - before).isEqualTo(VolumeRamp.STEPS)
        assertThat(volumes[before]).isEqualTo(VolumeRamp.levelAt(1))
        assertThat(volumes.last()).isEqualTo(1f)
    }
}
