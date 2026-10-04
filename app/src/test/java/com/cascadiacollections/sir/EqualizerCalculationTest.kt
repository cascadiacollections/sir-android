package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import com.cascadiacollections.sir.core.playback.calculateEqualizerLevels
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
        assertThat(levels).hasSize(1)
        assertThat(levels[0]).isEqualTo(0.toShort())
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
        assertThat(levels).hasSize(5)
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
            assertThat(level, name = "Level $level should be >= $minLevel").isGreaterThanOrEqualTo(minLevel)
            assertThat(level, name = "Level $level should be <= $maxLevel").isLessThanOrEqualTo(maxLevel)
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
            assertThat(
                levels[i],
                name = "Level at band $i (${levels[i]}) should be >= level at band ${i - 1} (${levels[i - 1]})"
            ).isGreaterThanOrEqualTo(levels[i - 1])
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
            assertThat(
                levels[i],
                name = "Level at band $i (${levels[i]}) should be <= level at band ${i - 1} (${levels[i - 1]})"
            ).isLessThanOrEqualTo(levels[i - 1])
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
            assertThat(level).isEqualTo(expected)
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
            assertThat(level).isEqualTo(500.toShort())
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
        assertThat(levels).hasSize(1)
        // position should be 0f / coerceAtLeast(1) = 0f, so level = minLevel + 0 = 0
        assertThat(levels[0]).isEqualTo(0.toShort())
    }

    @Test
    fun `bandCount of 0 returns empty list`() {
        val levels = calculateEqualizerLevels(
            bandCount = 0,
            minLevel = (-1500).toShort(),
            maxLevel = 1500.toShort(),
            range = 3000,
            curve = { 0.5f }
        )
        assertThat(levels).isEmpty()
    }

    @Test
    fun `large band count produces correct size and clamped values`() {
        val bandCount = 100
        val minLevel: Short = (-1500).toShort()
        val maxLevel: Short = 1500.toShort()
        val levels = calculateEqualizerLevels(
            bandCount = bandCount,
            minLevel = minLevel,
            maxLevel = maxLevel,
            range = 3000,
            curve = { pos -> pos }
        )
        assertThat(levels).hasSize(bandCount)
        levels.forEach { level ->
            assertThat(level, name = "Level $level must be >= $minLevel").isGreaterThanOrEqualTo(minLevel)
            assertThat(level, name = "Level $level must be <= $maxLevel").isLessThanOrEqualTo(maxLevel)
        }
    }

    @Test
    fun `curve returning negative value is clamped to minLevel`() {
        val minLevel: Short = 0.toShort()
        val maxLevel: Short = 1000.toShort()
        val levels = calculateEqualizerLevels(
            bandCount = 5,
            minLevel = minLevel,
            maxLevel = maxLevel,
            range = 1000,
            curve = { -1.0f } // Negative multiplier should clamp to minLevel
        )
        levels.forEach { level ->
            assertThat(level, name = "Negative curve should produce minLevel").isEqualTo(minLevel)
        }
    }

    @Test
    fun `band positions span full 0 to 1 range for multi-band config`() {
        val recordedPositions = mutableListOf<Float>()
        calculateEqualizerLevels(
            bandCount = 5,
            minLevel = 0.toShort(),
            maxLevel = 1000.toShort(),
            range = 1000
        ) { pos ->
            recordedPositions += pos
            0f
        }
        assertThat(recordedPositions).hasSize(5)
        assertThat(recordedPositions.first()).isCloseTo(0f, 0.001f)
        assertThat(recordedPositions.last()).isCloseTo(1f, 0.001f)
    }
}
