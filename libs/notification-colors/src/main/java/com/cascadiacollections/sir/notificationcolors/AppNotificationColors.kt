package com.cascadiacollections.sir.notificationcolors

/**
 * Shared media-notification accent color, used by both the phone and Wear playback services.
 * Must be kept in sync with `Amber40` in the app module's `ui/theme/Color.kt` — this is the
 * single source of truth so the two services can't drift out of sync with each other.
 */
object AppNotificationColors {
    const val ACCENT: Int = 0xFFFF8F00.toInt()
}
