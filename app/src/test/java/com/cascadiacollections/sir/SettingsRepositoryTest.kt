package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import com.cascadiacollections.sir.core.playback.EqualizerPreset
import com.cascadiacollections.sir.core.playback.SleepTimerDuration
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `fromMinutes returns correct SleepTimerDuration for valid values`() {
        assertThat(SleepTimerDuration.fromMinutes(15)).isEqualTo(SleepTimerDuration.FIFTEEN)
        assertThat(SleepTimerDuration.fromMinutes(30)).isEqualTo(SleepTimerDuration.THIRTY)
        assertThat(SleepTimerDuration.fromMinutes(45)).isEqualTo(SleepTimerDuration.FORTY_FIVE)
        assertThat(SleepTimerDuration.fromMinutes(60)).isEqualTo(SleepTimerDuration.SIXTY)
        assertThat(SleepTimerDuration.fromMinutes(90)).isEqualTo(SleepTimerDuration.NINETY)
        assertThat(SleepTimerDuration.fromMinutes(0)).isEqualTo(SleepTimerDuration.OFF)
    }

    @Test
    fun `fromMinutes returns OFF for invalid values`() {
        assertThat(SleepTimerDuration.fromMinutes(-1)).isEqualTo(SleepTimerDuration.OFF)
        assertThat(SleepTimerDuration.fromMinutes(44)).isEqualTo(SleepTimerDuration.OFF)
        assertThat(SleepTimerDuration.fromMinutes(120)).isEqualTo(SleepTimerDuration.OFF)
        assertThat(SleepTimerDuration.fromMinutes(Int.MAX_VALUE)).isEqualTo(SleepTimerDuration.OFF)
    }

    @Test
    fun `fromOrdinal returns correct EqualizerPreset for valid ordinals`() {
        assertThat(EqualizerPreset.fromOrdinal(0)).isEqualTo(EqualizerPreset.NORMAL)
        assertThat(EqualizerPreset.fromOrdinal(1)).isEqualTo(EqualizerPreset.BASS_BOOST)
        assertThat(EqualizerPreset.fromOrdinal(2)).isEqualTo(EqualizerPreset.VOCAL)
        assertThat(EqualizerPreset.fromOrdinal(3)).isEqualTo(EqualizerPreset.TREBLE)
    }

    @Test
    fun `fromOrdinal returns NORMAL for out-of-bounds ordinals`() {
        assertThat(EqualizerPreset.fromOrdinal(-1)).isEqualTo(EqualizerPreset.NORMAL)
        assertThat(EqualizerPreset.fromOrdinal(4)).isEqualTo(EqualizerPreset.NORMAL)
        assertThat(EqualizerPreset.fromOrdinal(100)).isEqualTo(EqualizerPreset.NORMAL)
        assertThat(EqualizerPreset.fromOrdinal(Int.MAX_VALUE)).isEqualTo(EqualizerPreset.NORMAL)
    }

    // SleepTimerDuration comprehensive tests

    @Test
    fun `SleepTimerDuration has exactly 6 entries`() {
        assertThat(SleepTimerDuration.entries).hasSize(6)
    }

    @Test
    fun `SleepTimerDuration minutes values are all unique`() {
        val minutes = SleepTimerDuration.entries.map { it.minutes }
        assertThat(minutes.toSet()).hasSize(minutes.size)
    }

    @Test
    fun `SleepTimerDuration labelRes are all valid resource ids`() {
        SleepTimerDuration.entries.forEach { duration ->
            assertThat(duration.labelRes, name = "labelRes for $duration should be a valid resource id").isNotEqualTo(0)
        }
    }

    @Test
    fun `SleepTimerDuration options match ShoutKit plus 90 minutes, in order`() {
        // Persisted by minutes, not ordinal, so the menu order is free to change.
        assertThat(SleepTimerDuration.entries.map { it.minutes })
            .containsExactly(0, 15, 30, 45, 60, 90)
    }

    @Test
    fun `SleepTimerDuration millisecond conversion is correct`() {
        assertThat(SleepTimerDuration.OFF.minutes * 60 * 1000L).isEqualTo(0L)
        assertThat(SleepTimerDuration.FIFTEEN.minutes * 60 * 1000L).isEqualTo(900_000L)
        assertThat(SleepTimerDuration.THIRTY.minutes * 60 * 1000L).isEqualTo(1_800_000L)
        assertThat(SleepTimerDuration.FORTY_FIVE.minutes * 60 * 1000L).isEqualTo(2_700_000L)
        assertThat(SleepTimerDuration.SIXTY.minutes * 60 * 1000L).isEqualTo(3_600_000L)
        assertThat(SleepTimerDuration.NINETY.minutes * 60 * 1000L).isEqualTo(5_400_000L)
    }

    // EqualizerPreset comprehensive tests

    @Test
    fun `EqualizerPreset has exactly 4 entries`() {
        assertThat(EqualizerPreset.entries).hasSize(4)
    }

    @Test
    fun `EqualizerPreset labelRes are all unique and valid`() {
        val labelResIds = EqualizerPreset.entries.map { it.labelRes }
        labelResIds.forEach { labelRes ->
            assertThat(labelRes, name = "labelRes should be a valid resource id").isNotEqualTo(0)
        }
        assertThat(labelResIds.toSet()).hasSize(labelResIds.size)
    }

    @Test
    fun `EqualizerPreset ordinal stability`() {
        assertThat(EqualizerPreset.NORMAL.ordinal).isEqualTo(0)
        assertThat(EqualizerPreset.BASS_BOOST.ordinal).isEqualTo(1)
        assertThat(EqualizerPreset.VOCAL.ordinal).isEqualTo(2)
        assertThat(EqualizerPreset.TREBLE.ordinal).isEqualTo(3)
    }
}
