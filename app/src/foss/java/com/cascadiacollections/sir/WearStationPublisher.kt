package com.cascadiacollections.sir

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * FOSS build of the phone→watch station sync: inert. The Wearable Data Layer is part of
 * proprietary Play services, so this flavor has no transport to the watch; the Wear app
 * then simply offers its default stream.
 */
object WearStationPublisher {

    /** Whether this build can sync to a watch at all. */
    const val isSupported: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun start(context: Context, scope: CoroutineScope) = Unit
}
