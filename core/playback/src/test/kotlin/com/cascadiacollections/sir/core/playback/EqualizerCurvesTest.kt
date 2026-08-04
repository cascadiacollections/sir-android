package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerCurvesTest {

    private val minLevel: Short = (-1500).toShort()
    private val maxLevel: Short = 1500.toShort()

    private fun levels(preset: EqualizerPreset, bands: Int = 5) =
        EqualizerCurves.levelsFor(preset, bands, minLevel, maxLevel)

    @Test
    fun `normal preset is flat at zero millibels`() {
        assertEquals(List(5) { 0.toShort() }, levels(EqualizerPreset.NORMAL))
    }

    @Test
    fun `normal preset stays flat for asymmetric level ranges`() {
        val asymmetric = EqualizerCurves.levelsFor(
            EqualizerPreset.NORMAL,
            bandCount = 5,
            minLevel = (-1200).toShort(),
            maxLevel = 400.toShort()
        )

        assertEquals(List(5) { 0.toShort() }, asymmetric)
    }

    @Test
    fun `bass boost is non-increasing across bands`() {
        val result = levels(EqualizerPreset.BASS_BOOST)

        assertTrue(result.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(result.first() > result.last())
    }

    @Test
    fun `treble boost is non-decreasing across bands`() {
        val result = levels(EqualizerPreset.TREBLE)

        assertTrue(result.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(result.last() > result.first())
    }

    @Test
    fun `vocal preset peaks in the mid bands`() {
        val result = levels(EqualizerPreset.VOCAL)

        assertEquals(600.toShort(), result[2])
        assertTrue(result[2] > result[0])
        assertTrue(result[2] > result[4])
    }

    @Test
    fun `levels never escape the supported range`() {
        EqualizerPreset.entries.forEach { preset ->
            levels(preset, bands = 10).forEach { level ->
                assertTrue("$preset produced $level", level in minLevel..maxLevel)
            }
        }
    }

    @Test
    fun `single band uses position zero`() {
        val result = calculateEqualizerLevels(1, minLevel, maxLevel, 3000) { pos -> 1f - pos }

        assertEquals(listOf(1500.toShort()), result)
    }

    @Test
    fun `curve output above one is clamped to max level`() {
        val result = calculateEqualizerLevels(5, minLevel, maxLevel, 3000) { 2f }

        assertTrue(result.all { it == maxLevel })
    }

    @Test
    fun `a curve overshooting the range clamps to the nearest rail`() {
        // Narrowing to Short before clamping truncated to 16 bits first, so 40_000 wrapped
        // to -25_536 and was then clamped to the *bottom* of the range instead of the top.
        val high = calculateEqualizerLevels(
            bandCount = 1,
            minLevel = 0.toShort(),
            maxLevel = 1_000.toShort(),
            range = 1_000,
            curve = { 40f }
        )

        assertEquals(listOf(1_000.toShort()), high)
    }

    @Test
    fun `a curve undershooting the range clamps to the lower rail`() {
        val low = calculateEqualizerLevels(
            bandCount = 1,
            minLevel = 0.toShort(),
            maxLevel = 1_000.toShort(),
            range = 1_000,
            curve = { -40f }
        )

        assertEquals(listOf(0.toShort()), low)
    }
}
