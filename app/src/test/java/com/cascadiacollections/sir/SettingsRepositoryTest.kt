package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `fromMinutes returns correct SleepTimerDuration for valid values`() {
        assertEquals(SleepTimerDuration.FIFTEEN, SleepTimerDuration.fromMinutes(15))
        assertEquals(SleepTimerDuration.THIRTY, SleepTimerDuration.fromMinutes(30))
        assertEquals(SleepTimerDuration.SIXTY, SleepTimerDuration.fromMinutes(60))
        assertEquals(SleepTimerDuration.NINETY, SleepTimerDuration.fromMinutes(90))
        assertEquals(SleepTimerDuration.OFF, SleepTimerDuration.fromMinutes(0))
    }

    @Test
    fun `fromMinutes returns OFF for invalid values`() {
        assertEquals(SleepTimerDuration.OFF, SleepTimerDuration.fromMinutes(-1))
        assertEquals(SleepTimerDuration.OFF, SleepTimerDuration.fromMinutes(45))
        assertEquals(SleepTimerDuration.OFF, SleepTimerDuration.fromMinutes(120))
        assertEquals(SleepTimerDuration.OFF, SleepTimerDuration.fromMinutes(Int.MAX_VALUE))
    }

    @Test
    fun `fromOrdinal returns correct EqualizerPreset for valid ordinals`() {
        assertEquals(EqualizerPreset.NORMAL, EqualizerPreset.fromOrdinal(0))
        assertEquals(EqualizerPreset.BASS_BOOST, EqualizerPreset.fromOrdinal(1))
        assertEquals(EqualizerPreset.VOCAL, EqualizerPreset.fromOrdinal(2))
        assertEquals(EqualizerPreset.TREBLE, EqualizerPreset.fromOrdinal(3))
    }

    @Test
    fun `fromOrdinal returns NORMAL for out-of-bounds ordinals`() {
        assertEquals(EqualizerPreset.NORMAL, EqualizerPreset.fromOrdinal(-1))
        assertEquals(EqualizerPreset.NORMAL, EqualizerPreset.fromOrdinal(4))
        assertEquals(EqualizerPreset.NORMAL, EqualizerPreset.fromOrdinal(100))
        assertEquals(EqualizerPreset.NORMAL, EqualizerPreset.fromOrdinal(Int.MAX_VALUE))
    }

    // SleepTimerDuration comprehensive tests

    @Test
    fun `SleepTimerDuration has exactly 5 entries`() {
        assertEquals(5, SleepTimerDuration.entries.size)
    }

    @Test
    fun `SleepTimerDuration minutes values are all unique`() {
        val minutes = SleepTimerDuration.entries.map { it.minutes }
        assertEquals(minutes.size, minutes.toSet().size)
    }

    @Test
    fun `SleepTimerDuration labels are all non-blank`() {
        SleepTimerDuration.entries.forEach { duration ->
            assertTrue("Label for $duration should not be blank", duration.label.isNotBlank())
        }
    }

    @Test
    fun `SleepTimerDuration ordinal stability`() {
        assertEquals(0, SleepTimerDuration.OFF.ordinal)
        assertEquals(1, SleepTimerDuration.FIFTEEN.ordinal)
        assertEquals(2, SleepTimerDuration.THIRTY.ordinal)
        assertEquals(3, SleepTimerDuration.SIXTY.ordinal)
        assertEquals(4, SleepTimerDuration.NINETY.ordinal)
    }

    @Test
    fun `SleepTimerDuration millisecond conversion is correct`() {
        assertEquals(0L, SleepTimerDuration.OFF.minutes * 60 * 1000L)
        assertEquals(900_000L, SleepTimerDuration.FIFTEEN.minutes * 60 * 1000L)
        assertEquals(1_800_000L, SleepTimerDuration.THIRTY.minutes * 60 * 1000L)
        assertEquals(3_600_000L, SleepTimerDuration.SIXTY.minutes * 60 * 1000L)
        assertEquals(5_400_000L, SleepTimerDuration.NINETY.minutes * 60 * 1000L)
    }

    // EqualizerPreset comprehensive tests

    @Test
    fun `EqualizerPreset has exactly 4 entries`() {
        assertEquals(4, EqualizerPreset.entries.size)
    }

    @Test
    fun `EqualizerPreset labels are all unique and non-blank`() {
        val labels = EqualizerPreset.entries.map { it.label }
        labels.forEach { label ->
            assertTrue("Label should not be blank", label.isNotBlank())
        }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `EqualizerPreset ordinal stability`() {
        assertEquals(0, EqualizerPreset.NORMAL.ordinal)
        assertEquals(1, EqualizerPreset.BASS_BOOST.ordinal)
        assertEquals(2, EqualizerPreset.VOCAL.ordinal)
        assertEquals(3, EqualizerPreset.TREBLE.ordinal)
    }
}
