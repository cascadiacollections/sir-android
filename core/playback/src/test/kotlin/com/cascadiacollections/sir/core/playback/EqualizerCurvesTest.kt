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

    // ---- levelsForCustomBands ----

    @Test
    fun `all-flat custom gains produce zero millibels`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = List(EqualizerCurves.CUSTOM_BAND_COUNT) { 0f },
            bandCount = 5,
            minLevel = minLevel,
            maxLevel = maxLevel
        )

        assertEquals(List(5) { 0.toShort() }, result)
    }

    @Test
    fun `a full boost reaches max level regardless of range asymmetry`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = listOf(1f, 1f, 1f, 1f, 1f),
            bandCount = 3,
            minLevel = (-3000).toShort(),
            maxLevel = 300.toShort()
        )

        assertEquals(List(3) { 300.toShort() }, result)
    }

    @Test
    fun `a full cut reaches min level regardless of range asymmetry`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = listOf(-1f, -1f, -1f, -1f, -1f),
            bandCount = 3,
            minLevel = (-3000).toShort(),
            maxLevel = 300.toShort()
        )

        assertEquals(List(3) { (-3000).toShort() }, result)
    }

    @Test
    fun `hardware bands interpolate between adjacent UI sliders`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = listOf(0f, 1f),
            bandCount = 3,
            minLevel = minLevel,
            maxLevel = maxLevel
        )

        // position 0 -> gain 0, position 0.5 -> gain 0.5, position 1 -> gain 1
        assertEquals(listOf(0.toShort(), 750.toShort(), 1500.toShort()), result)
    }

    @Test
    fun `custom band levels never escape the supported range`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = listOf(-1f, 1f, -1f, 1f, -1f),
            bandCount = 10,
            minLevel = minLevel,
            maxLevel = maxLevel
        )

        result.forEach { level -> assertTrue(level in minLevel..maxLevel) }
    }

    @Test
    fun `empty gains produce a flat curve rather than crashing`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = emptyList(),
            bandCount = 5,
            minLevel = minLevel,
            maxLevel = maxLevel
        )

        assertEquals(List(5) { 0.toShort() }, result)
    }

    // ---- normalizeCustomBands ----

    @Test
    fun `normalizing an empty list produces flat gains at the requested count`() {
        assertEquals(List(5) { 0f }, EqualizerCurves.normalizeCustomBands(emptyList(), bandCount = 5))
    }

    @Test
    fun `normalizing a correctly-sized in-range list is a no-op`() {
        val gains = listOf(-1f, -0.5f, 0f, 0.5f, 1f)
        assertEquals(gains, EqualizerCurves.normalizeCustomBands(gains, bandCount = 5))
    }

    @Test
    fun `normalizing a shorter list stretches it to the requested count`() {
        val result = EqualizerCurves.normalizeCustomBands(listOf(0f, 1f), bandCount = 5)
        assertEquals(5, result.size)
        assertEquals(0f, result.first())
        assertEquals(1f, result.last())
    }

    @Test
    fun `normalizing a longer list still produces exactly the requested count`() {
        val result = EqualizerCurves.normalizeCustomBands(List(9) { 1f }, bandCount = 5)
        assertEquals(5, result.size)
    }

    @Test
    fun `normalizing clamps out-of-range gains to -1f to 1f`() {
        val result = EqualizerCurves.normalizeCustomBands(listOf(-5f, 5f), bandCount = 3)
        result.forEach { gain -> assertTrue(gain in -1f..1f) }
    }

    // ---- displayGainsFor ----

    @Test
    fun `normal preset displays as all-flat gains`() {
        assertEquals(List(5) { 0f }, EqualizerCurves.displayGainsFor(EqualizerPreset.NORMAL, bandCount = 5))
    }

    @Test
    fun `bass boost displays as a descending gain curve`() {
        val result = EqualizerCurves.displayGainsFor(EqualizerPreset.BASS_BOOST, bandCount = 5)

        assertTrue(result.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(result.first() > result.last())
    }

    @Test
    fun `display gains stay within -1f to 1f`() {
        EqualizerPreset.entries.forEach { preset ->
            EqualizerCurves.displayGainsFor(preset, bandCount = 8).forEach { gain ->
                assertTrue("$preset produced $gain", gain in -1f..1f)
            }
        }
    }
}
