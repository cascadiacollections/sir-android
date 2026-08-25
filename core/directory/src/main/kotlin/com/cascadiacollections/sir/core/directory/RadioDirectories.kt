package com.cascadiacollections.sir.core.directory

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Composition root for the directory layer.
 *
 * Consumers ask for a [RadioDirectory] and get the full decorator chain; the ordering
 * of the decorators is an implementation detail owned here rather than duplicated at
 * every call site.
 */
object RadioDirectories {

    /**
     * Builds `CuratedFallback(Caching(RadioBrowser))`.
     *
     * Caching sits closest to the network so only real responses are memoized, and the
     * curated fallback wraps everything so an outage degrades instead of failing.
     */
    fun create(
        httpClient: OkHttpClient = defaultHttpClient(),
        mirrorProvider: MirrorProvider = RotatingMirrorProvider(),
        userAgent: String = RadioBrowserDirectory.DEFAULT_USER_AGENT
    ): RadioDirectory = CuratedFallbackDirectory(
        CachingRadioDirectory(
            RadioBrowserDirectory(
                httpClient = httpClient,
                mirrorProvider = mirrorProvider,
                userAgent = userAgent
            )
        )
    )

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
