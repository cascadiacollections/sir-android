package com.cascadiacollections.sir

import android.content.Context
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

/**
 * ShoutKit's opt-in Spatial Audio, on Android's platform spatializer.
 *
 * Off by default, as in ShoutKit: radio streams are plain stereo or mono, and
 * virtualizing them is a taste, not a fix. Off maps to `SPATIALIZATION_BEHAVIOR_NEVER`
 * (rather than the platform default, AUTO) so the setting means what it says on devices
 * that would otherwise spatialize on their own.
 */
internal object SpatialAudio {

    /** Media playback attributes, spatialized only when [enabled]. */
    fun audioAttributes(enabled: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setSpatializationBehavior(
            if (enabled) C.SPATIALIZATION_BEHAVIOR_AUTO else C.SPATIALIZATION_BEHAVIOR_NEVER
        )
        .build()

    /**
     * Whether this device has a spatializer at all (Android 12L+ with an immersive level),
     * so the setting is only offered where it can do something.
     */
    fun isSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return false
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        return audioManager.spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
    }
}
