package com.cascadiacollections.sir.okhttp.streaming

import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * Creates a pre-configured [OkHttpClient.Builder] optimized for live audio streaming:
 * - Connection pooling for instant reconnects on network switches
 * - HTTP/2 with HTTP/1.1 fallback
 * - DNS caching for faster reconnects
 * - Modern TLS only
 * - No overall call timeout (streaming is open-ended)
 *
 * Returns a [Builder][OkHttpClient.Builder] so callers can add module-specific
 * customizations (e.g. debug logging interceptor) before building.
 */
object StreamingHttpClientFactory {

    fun newBuilder(): OkHttpClient.Builder {
        val connectionPool = ConnectionPool(
            maxIdleConnections = 2,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )

        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dns(CachingDns())
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
    }
}
