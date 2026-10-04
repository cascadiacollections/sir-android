package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.playback.VolumeRamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

        assertEquals(0f, volumes.first(), 0f)
        assertEquals(VolumeRamp.STEPS + 1, volumes.size)
        assertEquals(0.5f, halfway, 0.08f)
        assertEquals(1f, volumes.last(), 0f)
        assertEquals(volumes, volumes.sorted())
    }

    @Test
    fun `stopping mid-fade cancels the ramp and stays silent`() = runTest {
        val volumes = mutableListOf<Float>()
        val fader = VolumeFader(backgroundScope) { volumes += it }

        fader.fadeIn()
        advanceTimeBy(VolumeRamp.DURATION_MS / 2)
        fader.silence()
        finishRamp()

        assertEquals(0f, volumes.last(), 0f)
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
        assertEquals(VolumeRamp.STEPS, volumes.size - before)
        assertEquals(VolumeRamp.levelAt(1), volumes[before], 0f)
        assertEquals(1f, volumes.last(), 0f)
    }
}
