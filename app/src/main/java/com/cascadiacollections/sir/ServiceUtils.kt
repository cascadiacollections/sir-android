package com.cascadiacollections.sir

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Start the [RadioPlaybackService] as a foreground service if not already running.
 * Shared between [MainActivity], [RadioTileService], and any other entry point.
 */
fun Context.ensureRadioServiceRunning() {
    val intent = Intent(this, RadioPlaybackService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(this, intent)
    } else {
        startService(intent)
    }
}

