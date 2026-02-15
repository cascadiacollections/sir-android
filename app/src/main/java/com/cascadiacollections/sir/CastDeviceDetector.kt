package com.cascadiacollections.sir

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battery-efficient Cast device detector.
 * Only scans when:
 * - App is in foreground (RESUMED state)
 * - Device is connected to WiFi
 * - Cast module is not already installed
 *
 * Stops scanning immediately when devices are found or app goes to background.
 */
class CastDeviceDetector(
    private val context: Context
) : DefaultLifecycleObserver {

    private val _castDevicesAvailable = MutableStateFlow(false)
    val castDevicesAvailable: StateFlow<Boolean> = _castDevicesAvailable.asStateFlow()

    private var mediaRouter: MediaRouter? = null
    private var isScanning = false

    private val routeSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        .build()

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            checkForCastDevices(router)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            checkForCastDevices(router)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            checkForCastDevices(router)
        }
    }

    private fun checkForCastDevices(router: MediaRouter) {
        router.routes
            .any { it.matchesSelector(routeSelector) && !it.isDefault }
            .takeIf { it && !_castDevicesAvailable.value }
            ?.let {
                _castDevicesAvailable.value = true
                // Stop scanning once devices are found to save battery
                stopScanning()
            }
    }

    override fun onResume(owner: LifecycleOwner) {
        // Only scan if on WiFi to save battery (Cast requires WiFi anyway)
        if (isOnWifi()) {
            startScanning()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        stopScanning()
    }

    private fun startScanning() {
        if (isScanning) return

        try {
            mediaRouter = MediaRouter.getInstance(context)
            mediaRouter?.addCallback(
                routeSelector,
                routerCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
            isScanning = true
        } catch (_: Exception) {
            // MediaRouter not available
            isScanning = false
        }
    }

    private fun stopScanning() {
        if (!isScanning) return

        try {
            mediaRouter?.removeCallback(routerCallback)
        } catch (_: Exception) {
            // Ignore
        }
        isScanning = false
    }

    private fun isOnWifi(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Reset detection state (e.g., after cast module is installed)
     */
    fun resetDetection() {
        _castDevicesAvailable.value = false
    }

    fun release() {
        stopScanning()
        mediaRouter = null
    }
}

