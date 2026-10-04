package com.cascadiacollections.sir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.cascadiacollections.sir.core.model.Station

/**
 * "Copy automation link": puts `sir://play/{id}` on the clipboard, for pasting into a
 * routine, Tasker or MacroDroid. Opening it plays the station with no UI
 * ([PlayStationActivity]).
 */
object AutomationLinks {

    /** The link that plays [station]. */
    fun linkFor(station: Station): String = StationDeepLink.playLink(station.id).toString()

    fun copy(context: Context, station: Station) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val label = context.getString(R.string.automation_link_clip_label)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, linkFor(station)))
        // Android 13+ confirms every copy itself; a Toast on top would say it twice.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.automation_link_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
