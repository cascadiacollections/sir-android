package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import org.junit.Test

class EqualizerCurvesTest {

    private val minLevel: Short = (-1500).toShort()
    private val maxLevel: Short = 1500.toShort()

    private fun levels(preset: EqualizerPreset, bands: Int = 5) =
        EqualizerCurves.levelsFor(preset, bands, minLevel, maxLevel)

    @Test
    fun `normal preset is flat at zero millibels`() {
        assertThat(levels(EqualizerPreset.NORMAL)).isEqualTo(List(5) { 0.toShort() })
    }

    @Test
    fun `normal preset stays flat for asymmetric level ranges`() {
        val asymmetric = EqualizerCurves.levelsFor(
            EqualizerPreset.NORMAL,
            bandCount = 5,
            minLevel = (-1200).toShort(),
            maxLevel = 400.toShort()
        )

        assertThat(asymmetric).isEqualTo(List(5) { 0.toShort() })
    }

    @Test
    fun `bass boost is non-increasing across bands`() {
        val result = levels(EqualizerPreset.BASS_BOOST)

        assertThat(result.zipWithNext().all { (a, b) -> b <= a }).isTrue()
        assertThat(result.first()).isGreaterThan(result.last())
    }

    @Test
    fun `treble boost is non-decreasing across bands`() {
        val result = levels(EqualizerPreset.TREBLE)

        assertThat(result.zipWithNext().all { (a, b) -> b >= a }).isTrue()
        assertThat(result.last()).isGreaterThan(result.first())
    }

    @Test
    fun `vocal preset peaks in the mid bands`() {
        val result = levels(EqualizerPreset.VOCAL)

        assertThat(result[2]).isEqualTo(600.toShort())
        assertThat(result[2]).isGreaterThan(result[0])
        assertThat(result[2]).isGreaterThan(result[4])
    }

    @Test
    fun `levels never escape the supported range`() {
        EqualizerPreset.entries.forEach { preset ->
            levels(preset, bands = 10).forEach { level ->
                assertThat(level, name = "$preset produced $level").isBetween(minLevel, maxLevel)
            }
        }
    }

    @Test
    fun `single band uses position zero`() {
        val result = calculateEqualizerLevels(1, minLevel, maxLevel, 3000) { pos -> 1f - pos }

        assertThat(result).containsExactly(1500.toShort())
    }

    @Test
    fun `curve output above one is clamped to max level`() {
        val result = calculateEqualizerLevels(5, minLevel, maxLevel, 3000) { 2f }

        assertThat(result).each { it.isEqualTo(maxLevel) }
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

        assertThat(high).containsExactly(1_000.toShort())
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

        assertThat(low).containsExactly(0.toShort())
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

        assertThat(result).isEqualTo(List(5) { 0.toShort() })
    }

    @Test
    fun `a full boost reaches max level regardless of range asymmetry`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = listOf(1f, 1f, 1f, 1f, 1f),
            bandCount = 3,
            minLevel = (-3000).toShort(),
            maxLevel = 300.toShort()
        )

        assertThat(result).isEqualTo(List(3) { 300.toShort() })
    }

    @Test
    fun `a full cut reaches min level regardless of range asymmetry`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = listOf(-1f, -1f, -1f, -1f, -1f),
            bandCount = 3,
            minLevel = (-3000).toShort(),
            maxLevel = 300.toShort()
        )

        assertThat(result).isEqualTo(List(3) { (-3000).toShort() })
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
        assertThat(result).containsExactly(0.toShort(), 750.toShort(), 1500.toShort())
    }

    @Test
    fun `custom band levels never escape the supported range`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = listOf(-1f, 1f, -1f, 1f, -1f),
            bandCount = 10,
            minLevel = minLevel,
            maxLevel = maxLevel
        )

        result.forEach { level -> assertThat(level).isBetween(minLevel, maxLevel) }
    }

    @Test
    fun `empty gains produce a flat curve rather than crashing`() {
        val result = EqualizerCurves.levelsForCustomBands(
            gains = emptyList(),
            bandCount = 5,
            minLevel = minLevel,
            maxLevel = maxLevel
        )

        assertThat(result).isEqualTo(List(5) { 0.toShort() })
    }

    // ---- normalizeCustomBands ----

    @Test
    fun `normalizing an empty list produces flat gains at the requested count`() {
        assertThat(EqualizerCurves.normalizeCustomBands(emptyList(), bandCount = 5))
            .isEqualTo(List(5) { 0f })
    }

    @Test
    fun `normalizing a correctly-sized in-range list is a no-op`() {
        val gains = listOf(-1f, -0.5f, 0f, 0.5f, 1f)
        assertThat(EqualizerCurves.normalizeCustomBands(gains, bandCount = 5)).isEqualTo(gains)
    }

    @Test
    fun `normalizing a shorter list stretches it to the requested count`() {
        val result = EqualizerCurves.normalizeCustomBands(listOf(0f, 1f), bandCount = 5)
        assertThat(result).hasSize(5)
        assertThat(result.first()).isEqualTo(0f)
        assertThat(result.last()).isEqualTo(1f)
    }

    @Test
    fun `normalizing a longer list still produces exactly the requested count`() {
        val result = EqualizerCurves.normalizeCustomBands(List(9) { 1f }, bandCount = 5)
        assertThat(result).hasSize(5)
    }

    @Test
    fun `normalizing clamps out-of-range gains to -1f to 1f`() {
        val result = EqualizerCurves.normalizeCustomBands(listOf(-5f, 5f), bandCount = 3)
        result.forEach { gain -> assertThat(gain).isBetween(-1f, 1f) }
    }

    // ---- displayGainsFor ----

    @Test
    fun `normal preset displays as all-flat gains`() {
        assertThat(EqualizerCurves.displayGainsFor(EqualizerPreset.NORMAL, bandCount = 5))
            .isEqualTo(List(5) { 0f })
    }

    @Test
    fun `bass boost displays as a descending gain curve`() {
        val result = EqualizerCurves.displayGainsFor(EqualizerPreset.BASS_BOOST, bandCount = 5)

        assertThat(result.zipWithNext().all { (a, b) -> b <= a }).isTrue()
        assertThat(result.first()).isGreaterThan(result.last())
    }

    @Test
    fun `display gains stay within -1f to 1f`() {
        EqualizerPreset.entries.forEach { preset ->
            EqualizerCurves.displayGainsFor(preset, bandCount = 8).forEach { gain ->
                assertThat(gain, name = "$preset produced $gain").isBetween(-1f, 1f)
            }
        }
    }

    @Test
    fun `a non-positive band count produces empty display gains rather than crashing`() {
        assertThat(EqualizerCurves.displayGainsFor(EqualizerPreset.BASS_BOOST, bandCount = 0)).isEmpty()
        assertThat(EqualizerCurves.displayGainsFor(EqualizerPreset.BASS_BOOST, bandCount = -1)).isEmpty()
    }

    @Test
    fun `flat is located from the actual range rather than assumed at the midpoint`() {
        // minLevel=-1200, maxLevel=400: 0 mB sits at fraction 1200/1600 = 0.75, not 0.5.
        // BASS_BOOST's curve is 0.6 at position 0 (band 0 of a single-band sample), which
        // is below that 0.75 flat point, so it must display as a *cut*, not the boost a
        // symmetric-midpoint assumption (0.6 > 0.5) would have shown.
        val result = EqualizerCurves.displayGainsFor(
            EqualizerPreset.BASS_BOOST,
            bandCount = 1,
            minLevel = (-1200).toShort(),
            maxLevel = 400.toShort()
        )
        assertThat(result.single()).isCloseTo(-0.2f, 0.001f)
    }

    @Test
    fun `display gains agree with levelsFor's actual applied level, on an asymmetric range`() {
        val minLevel = (-1200).toShort()
        val maxLevel = 400.toShort()
        EqualizerPreset.entries.forEach { preset ->
            val gains = EqualizerCurves.displayGainsFor(preset, bandCount = 5, minLevel = minLevel, maxLevel = maxLevel)
            val appliedLevels = EqualizerCurves.levelsFor(
                preset,
                bandCount = 5,
                minLevel = minLevel,
                maxLevel = maxLevel
            )
            val gainAsLevels = EqualizerCurves.levelsForCustomBands(
                gains,
                bandCount = 5,
                minLevel = minLevel,
                maxLevel = maxLevel
            )
            // Small rounding slop from the two independent Short-narrowing paths.
            appliedLevels.zip(gainAsLevels).forEach { (applied, fromGain) ->
                assertThat(
                    kotlin.math.abs(applied - fromGain),
                    name = "$preset: applied=$applied fromDisplayGain=$fromGain"
                ).isLessThanOrEqualTo(1)
            }
        }
    }
}
