package com.cascadiacollections.sir.core.playback

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager

/**
 * The CPU and WiFi locks a streaming session needs to survive doze.
 *
 * Both locks are acquired and released as a pair around playback, and both are
 * idempotent: acquiring twice would make the release refcount wrong and leak the lock
 * past the end of playback, which shows up as battery drain rather than as a crash.
 */
class PlaybackLocks(context: Context, tagPrefix: String = DEFAULT_TAG_PREFIX) {

    private val appContext = context.applicationContext

    private val wakeLock: PowerManager.WakeLock? =
        appContext.getSystemService(PowerManager::class.java)?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$tagPrefix::PlaybackWakeLock"
        )

    // WIFI_MODE_FULL_LOW_LATENCY (API 29+) measurably reduces buffering on modern
    // WiFi 6/7 chipsets; older devices only have the high-performance mode.
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

    /** True when either lock is currently held. */
    val isHeld: Boolean get() = wakeLock?.isHeld == true || wifiLock?.isHeld == true

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        wakeLock?.takeUnless { it.isHeld }?.acquire()
        wifiLock?.takeUnless { it.isHeld }?.acquire()
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        const val DEFAULT_TAG_PREFIX: String = "SIR"
    }
}
