package com.cascadiacollections.sir

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FOSS build: no Cast discovery.
 *
 * The Play implementation scans with `androidx.mediarouter`, which only surfaces Cast
 * receivers when the Cast framework has registered its route provider — so in a build
 * with no Cast module the scan is guaranteed to find nothing while still holding a
 * discovery callback and waking the radio. Reporting `false` outright is both honest and
 * cheaper, and it lets `mediarouter` drop out of the FOSS dependency graph.
 *
 * Keeping [DefaultLifecycleObserver] means `MainActivity` can register this without a
 * flavor check; the callbacks are simply never overridden.
 */
@Suppress("UNUSED_PARAMETER")
class CastDeviceDetector(context: Context) : DefaultLifecycleObserver {

    private val _castDevicesAvailable = MutableStateFlow(false)
    val castDevicesAvailable: StateFlow<Boolean> = _castDevicesAvailable.asStateFlow()

    fun resetDetection() = Unit

    fun release() = Unit
}
