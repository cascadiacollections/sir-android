package com.cascadiacollections.sir.core.directory

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Composition root for the directory layer.
 *
 * Consumers ask for a [RadioDirectory] and get the full decorator chain; the ordering
 * of the decorators is an implementation detail owned here rather than duplicated at
 * every call site.
 */
object RadioDirectories {

    /**
     * Builds `CuratedFallback(Snapshot(Caching(RadioBrowser)))`, or
     * `CuratedFallback(Caching(RadioBrowser))` when there is no [snapshotStore].
     *
     * Caching sits closest to the network so only real responses are memoized. The
     * on-disk discovery snapshot sits above it, so a stale-while-revalidate refresh can
     * still be answered by a response cached moments earlier, and below the curated
     * fallback, so bundled stations are only shown when there is no snapshot at all. The
     * fallback wraps everything so an outage degrades instead of failing.
     */
    fun create(
        httpClient: OkHttpClient = defaultHttpClient(),
        userAgent: String = RadioBrowserDirectory.DEFAULT_USER_AGENT,
        mirrorProvider: MirrorProvider = discoveringMirrorProvider(httpClient, userAgent),
        snapshotStore: DiscoverySnapshotStore? = null,
        backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ): RadioDirectory {
        val cached = CachingRadioDirectory(
            RadioBrowserDirectory(
                httpClient = httpClient,
                mirrorProvider = mirrorProvider,
                userAgent = userAgent
            )
        )
        val persisted = snapshotStore?.let {
            SnapshotRadioDirectory(cached, it, backgroundScope)
        } ?: cached
        return CuratedFallbackDirectory(persisted)
    }

    /**
     * Mirrors discovered from `all.api.radio-browser.info/json/servers`, cached for the
     * process and falling back to [RotatingMirrorProvider.DEFAULT_MIRRORS].
     */
    fun discoveringMirrorProvider(
        httpClient: OkHttpClient,
        userAgent: String = RadioBrowserDirectory.DEFAULT_USER_AGENT
    ): MirrorProvider = DiscoveringMirrorProvider(RadioBrowserServerListSource(httpClient, userAgent))

    /**
     * Directory-sized HTTP client: short timeouts because these are small JSON calls
     * on a user-blocking path, unlike the long-lived streaming client.
     */
    fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
