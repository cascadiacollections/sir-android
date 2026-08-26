package com.cascadiacollections.sir.core.playback

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * The WiFi low-latency lock a streaming session needs to survive doze.
 *
 * ExoPlayer's own `setWakeMode(C.WAKE_MODE_NETWORK)` already acquires a `PowerManager`
 * wake lock and a `WifiManager.WifiLock` for as long as the player is buffering or
 * playing, so this class does not duplicate either — Media3 1.11.0's own WiFi lock is
 * hardcoded to `WIFI_MODE_FULL_HIGH_PERF`, with no API-level branch, so the one thing
 * left worth acquiring separately is `WIFI_MODE_FULL_LOW_LATENCY` on API 29+, which
 * measurably reduces buffering on modern WiFi 6/7 chipsets. Idempotent: acquiring twice
 * would make the release refcount wrong and leak the lock past the end of playback,
 * which shows up as battery drain rather than as a crash.
 */
class PlaybackLocks(context: Context, tagPrefix: String = DEFAULT_TAG_PREFIX) {

    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val wifiLock: WifiManager.WifiLock? =
        appContext.getSystemService(WifiManager::class.java)?.let { wifiManager ->
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiManager.createWifiLock(mode, "$tagPrefix::PlaybackWifiLock")
        }

    /** True when the lock is currently held. */
    val isHeld: Boolean get() = wifiLock?.isHeld == true

    fun acquire() {
        wifiLock?.takeUnless { it.isHeld }?.acquire()
    }

    fun release() {
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        const val DEFAULT_TAG_PREFIX: String = "SIR"
    }
}
