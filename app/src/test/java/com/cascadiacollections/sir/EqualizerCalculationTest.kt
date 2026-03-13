package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerCalculationTest {

    @Test
    fun `single band returns correct level`() {
        val levels = calculateEqualizerLevels(
            bandCount = 1,
            minLevel = (-1500).toShort(),
            maxLevel = 1500.toShort(),
            range = 3000,
            curve = { 0.5f }
        )
        assertEquals(1, levels.size)
        assertEquals(0.toShort(), levels[0])
    }

    @Test
    fun `multiple bands produce correct count`() {
        val levels = calculateEqualizerLevels(
            bandCount = 5,
            minLevel = 0.toShort(),
            maxLevel = 100.toShort(),
            range = 100,
            curve = { 0.5f }
        )
        assertEquals(5, levels.size)
    }

    @Test
    fun `output levels are clamped within minLevel to maxLevel range`() {
        val minLevel: Short = (-1500).toShort()
        val maxLevel: Short = 1500.toShort()
        val levels = calculateEqualizerLevels(
            bandCount = 5,
            minLevel = minLevel,
            maxLevel = maxLevel,
            range = 3000,
            curve = { 2.0f } // Exceeds 1.0 multiplier — should be clamped
        )
        levels.forEach { level ->
            assertTrue("Level $level should be >= $minLevel", level >= minLevel)
            assertTrue("Level $level should be <= $maxLevel", level <= maxLevel)
        }
    }

    @Test
    fun `linear ascending curve produces increasing levels`() {
        val levels = calculateEqualizerLevels(
            bandCount = 5,
            minLevel = 0.toShort(),
            maxLevel = 1000.toShort(),
            range = 1000,
            curve = { pos -> pos }
        )
        for (i in 1 until levels.size) {
            assertTrue(
                "Level at band $i (${levels[i]}) should be >= level at band ${i - 1} (${levels[i - 1]})",
                levels[i] >= levels[i - 1]
            )
        }
    }

    @Test
    fun `linear descending curve produces decreasing levels`() {
        val levels = calculateEqualizerLevels(
            bandCount = 5,
            minLevel = 0.toShort(),
            maxLevel = 1000.toShort(),
            range = 1000,
            curve = { pos -> 1.0f - pos }
        )
        for (i in 1 until levels.size) {
            assertTrue(
                "Level at band $i (${levels[i]}) should be <= level at band ${i - 1} (${levels[i - 1]})",
                levels[i] <= levels[i - 1]
            )
        }
    }

    @Test
    fun `flat curve produces uniform levels`() {
        val levels = calculateEqualizerLevels(
            bandCount = 5,
            minLevel = (-1500).toShort(),
            maxLevel = 1500.toShort(),
            range = 3000,
            curve = { 0.5f }
        )
        val expected = levels[0]
        levels.forEach { level ->
            assertEquals(expected, level)
        }
    }

    @Test
    fun `zero range produces all same value`() {
        val levels = calculateEqualizerLevels(
            bandCount = 5,
            minLevel = 500.toShort(),
            maxLevel = 500.toShort(),
            range = 0,
            curve = { pos -> pos }
        )
        levels.forEach { level ->
            assertEquals(500.toShort(), level)
        }
    }

    @Test
    fun `bandCount of 1 does not divide by zero`() {
        val levels = calculateEqualizerLevels(
            bandCount = 1,
            minLevel = 0.toShort(),
            maxLevel = 1000.toShort(),
            range = 1000,
            curve = { pos -> pos }
        )
        assertEquals(1, levels.size)
        // position should be 0f / coerceAtLeast(1) = 0f, so level = minLevel + 0 = 0
        assertEquals(0.toShort(), levels[0])
    }
}
