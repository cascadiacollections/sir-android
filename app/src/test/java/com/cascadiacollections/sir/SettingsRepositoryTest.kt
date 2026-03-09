package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
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
}
