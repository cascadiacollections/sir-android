package com.cascadiacollections.sir

import com.cascadiacollections.sir.okhttp.streaming.StreamingHttpClientFactory
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient

object StreamingHttpClientProvider {
    val client: OkHttpClient by lazy {
        StreamingHttpClientFactory.newBuilder()
            .writeTimeout(10, TimeUnit.SECONDS)
            // The factory only allows modern TLS, which makes OkHttp itself refuse any
            // http:// URL before the network security config is even consulted. Station
            // streams are arbitrary third-party hosts and many are HTTP-only Icecast /
            // Shoutcast servers, so cleartext has to be allowed alongside TLS here.
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        okhttp3.logging.HttpLoggingInterceptor().setLevel(
                            okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS
                        )
                    )
                }
            }
            .build()
    }
}
