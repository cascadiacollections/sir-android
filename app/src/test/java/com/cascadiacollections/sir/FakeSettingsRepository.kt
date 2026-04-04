package com.cascadiacollections.sir

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake of [SettingsRepository] for unit tests.
 * Each preference is backed by a [MutableStateFlow] so mutations
 * are immediately visible to collectors without DataStore.
 */
class FakeSettingsRepository {

    private val _streamQuality = MutableStateFlow(StreamQuality.HIGH)
    val streamQuality: Flow<StreamQuality> = _streamQuality

    private val _chromecastEnabled = MutableStateFlow(false)
    val chromecastEnabled: Flow<Boolean> = _chromecastEnabled

    private val _sleepTimerDuration = MutableStateFlow(SleepTimerDuration.OFF)
    val sleepTimerDuration: Flow<SleepTimerDuration> = _sleepTimerDuration

    private val _sleepTimerFiresAt = MutableStateFlow(0L)
    val sleepTimerFiresAt: Flow<Long> = _sleepTimerFiresAt

    private val _equalizerPreset = MutableStateFlow(EqualizerPreset.NORMAL)
    val equalizerPreset: Flow<EqualizerPreset> = _equalizerPreset

    private val _customStreamUrl = MutableStateFlow<String?>(null)
    val customStreamUrl: Flow<String?> = _customStreamUrl

    suspend fun setStreamQuality(quality: StreamQuality) {
        _streamQuality.value = quality
    }

    suspend fun setChromecastEnabled(enabled: Boolean) {
        _chromecastEnabled.value = enabled
    }

    suspend fun setSleepTimerDuration(duration: SleepTimerDuration) {
        _sleepTimerDuration.value = duration
    }

    suspend fun setSleepTimerFiresAt(epochMillis: Long) {
        _sleepTimerFiresAt.value = if (epochMillis <= 0L) 0L else epochMillis
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
    }

    suspend fun setCustomStreamUrl(url: String?) {
        _customStreamUrl.value = url?.takeIf { it.isNotBlank() }
    }
}
