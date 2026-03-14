package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the equalizer preset curve functions used by [RadioPlaybackService].
 *
 * Each preset applies a specific curve to [calculateEqualizerLevels]:
 * - BASS_BOOST : `(1 - pos) * 0.6f`  — descending, max at low bands
 * - TREBLE     : `pos * 0.6f`         — ascending, max at high bands
 * - VOCAL      : piecewise 0.1 / 0.7 / 0.4 — mid-frequency boost
 * - NORMAL     : all bands set to 0 (no calculation needed)
 */
class EqualizerPresetCurveTest {

    // Parameters representative of a typical Android Equalizer (5 bands, ±15 dB range in millibels)
    private val bandCount = 5
    private val minLevel: Short = (-1500).toShort()
    private val maxLevel: Short = 1500.toShort()
    private val range = 3000

    // --- Bass Boost curve: (1 - pos) * 0.6f ---

    @Test
    fun `bass boost curve produces higher levels for low-frequency bands`() {
        val levels = bassBoostLevels()
        assertTrue(
            "Lowest band (${levels.first()}) should be boosted more than highest band (${levels.last()})",
            levels.first() > levels.last()
        )
    }

    @Test
    fun `bass boost curve produces non-increasing levels`() {
        val levels = bassBoostLevels()
        for (i in 1 until levels.size) {
            assertTrue(
                "Level at band $i (${levels[i]}) should be <= level at band ${i - 1} (${levels[i - 1]})",
                levels[i] <= levels[i - 1]
            )
        }
    }

    @Test
    fun `bass boost first band level equals expected value`() {
        // pos = 0 for bandCount = 1 → curve = (1 - 0) * 0.6 = 0.6
        // level = minLevel + range * 0.6 = -1500 + 1800 = 300
        val levels = calculateEqualizerLevels(
            bandCount = 1,
            minLevel = minLevel,
            maxLevel = maxLevel,
            range = range
        ) { pos -> (1 - pos) * 0.6f }
        assertEquals(300.toShort(), levels[0])
    }

    @Test
    fun `bass boost curve levels stay within valid millibel range`() {
        bassBoostLevels().forEach { level ->
            assertTrue("Level $level must be >= $minLevel", level >= minLevel)
            assertTrue("Level $level must be <= $maxLevel", level <= maxLevel)
        }
    }

    // --- Treble curve: pos * 0.6f ---

    @Test
    fun `treble curve produces higher levels for high-frequency bands`() {
        val levels = trebleLevels()
        assertTrue(
            "Highest band (${levels.last()}) should be boosted more than lowest band (${levels.first()})",
            levels.last() > levels.first()
        )
    }

    @Test
    fun `treble curve produces non-decreasing levels`() {
        val levels = trebleLevels()
        for (i in 1 until levels.size) {
            assertTrue(
                "Level at band $i (${levels[i]}) should be >= level at band ${i - 1} (${levels[i - 1]})",
                levels[i] >= levels[i - 1]
            )
        }
    }

    @Test
    fun `treble last band level equals expected value`() {
        // pos = 1.0 for the last of 5 bands → curve = 1.0 * 0.6 = 0.6
        // level = -1500 + 3000 * 0.6 = 300
        val levels = trebleLevels()
        assertEquals(300.toShort(), levels.last())
    }

    @Test
    fun `treble curve levels stay within valid millibel range`() {
        trebleLevels().forEach { level ->
            assertTrue("Level $level must be >= $minLevel", level >= minLevel)
            assertTrue("Level $level must be <= $maxLevel", level <= maxLevel)
        }
    }

    // --- Vocal (piecewise) curve ---

    @Test
    fun `vocal curve boosts mid-frequency band more than bass band`() {
        val levels = vocalLevels()
        // With 5 bands: positions 0.0, 0.25, 0.5, 0.75, 1.0
        // band 0 (pos=0.00) → 0.1f (bass), band 2 (pos=0.50) → 0.7f (mid)
        assertTrue(
            "Mid band (${levels[2]}) should be louder than lowest bass band (${levels[0]})",
            levels[2] > levels[0]
        )
    }

    @Test
    fun `vocal curve boosts mid-frequency band more than treble band`() {
        val levels = vocalLevels()
        // band 2 (pos=0.50) → 0.7f (mid), band 4 (pos=1.00) → 0.4f (treble)
        assertTrue(
            "Mid band (${levels[2]}) should be louder than highest treble band (${levels[4]})",
            levels[2] > levels[4]
        )
    }

    @Test
    fun `vocal curve produces expected mid-band level`() {
        // band 2 (pos=0.5) → 0.7f  →  level = -1500 + 3000*0.7 = 600
        val levels = vocalLevels()
        assertEquals(600.toShort(), levels[2])
    }

    @Test
    fun `vocal curve levels stay within valid millibel range`() {
        vocalLevels().forEach { level ->
            assertTrue("Level $level must be >= $minLevel", level >= minLevel)
            assertTrue("Level $level must be <= $maxLevel", level <= maxLevel)
        }
    }

    // --- Preset curves with 10 bands (extended equalizer) ---

    @Test
    fun `bass boost curve with 10 bands produces correct count and descending shape`() {
        val levels = calculateEqualizerLevels(10, minLevel, maxLevel, range) { pos -> (1 - pos) * 0.6f }
        assertEquals(10, levels.size)
        assertTrue("First band should be higher than last band", levels.first() > levels.last())
    }

    @Test
    fun `treble curve with 10 bands produces correct count and ascending shape`() {
        val levels = calculateEqualizerLevels(10, minLevel, maxLevel, range) { pos -> pos * 0.6f }
        assertEquals(10, levels.size)
        assertTrue("Last band should be higher than first band", levels.last() > levels.first())
    }

    @Test
    fun `vocal curve with 10 bands boosts mid frequencies`() {
        val levels = calculateEqualizerLevels(10, minLevel, maxLevel, range) { pos ->
            when {
                pos < 0.3f -> 0.1f
                pos < 0.7f -> 0.7f
                else -> 0.4f
            }
        }
        assertEquals(10, levels.size)
        // With 10 bands, mid-range bands (positions ~0.3–0.7) should be higher than bass (pos 0)
        assertTrue("A mid band should be louder than the lowest band", levels[4] > levels[0])
    }

    // --- Helpers ---

    private fun bassBoostLevels() = calculateEqualizerLevels(bandCount, minLevel, maxLevel, range) { pos ->
        (1 - pos) * 0.6f
    }

    private fun trebleLevels() = calculateEqualizerLevels(bandCount, minLevel, maxLevel, range) { pos ->
        pos * 0.6f
    }

    private fun vocalLevels() = calculateEqualizerLevels(bandCount, minLevel, maxLevel, range) { pos ->
        when {
            pos < 0.3f -> 0.1f
            pos < 0.7f -> 0.7f
            else -> 0.4f
        }
    }
}
