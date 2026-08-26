package com.cascadiacollections.sir.notificationcolors

import androidx.annotation.ColorInt
import androidx.core.app.NotificationCompat

/**
 * Shared notification accent color used by both the phone (`RadioPlaybackService`) and
 * Wear (`WearPlaybackService`) media notifications, so `.setColor()`/`.setColorized(true)`
 * render identically across surfaces without each service duplicating a brand-color
 * constant. Matches `ui.theme.Amber40`, kept as a plain Int since this module has no
 * Compose dependency and must stay usable from plain `NotificationCompat.Builder` code.
 */
object NotificationAccentColor {
    @ColorInt
    val VALUE: Int = 0xFFFF8F00.toInt()

    fun applyTo(builder: NotificationCompat.Builder): NotificationCompat.Builder =
        builder.setColor(VALUE).setColorized(true)
}
