package com.cascadiacollections.sir.ui.theme

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Builds the app's shared Coil [ImageLoader] with a short, bounded call timeout.
 *
 * Coil's own default network engine builds a plain `OkHttpClient()` with no call
 * timeout at all, which would let a dead station artwork URL hang indefinitely rather
 * than failing fast into [com.cascadiacollections.sir.ui.StationRow]'s icon fallback.
 */
object AppImageLoader {

    private const val TIMEOUT_SECONDS = 10L

    // Built once and reused: the callFactory lambda below can be invoked more than
    // once, and a fresh OkHttpClient per call would defeat connection pooling.
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun create(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }
        .build()
}
