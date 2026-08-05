package com.cascadiacollections.sir

import com.cascadiacollections.sir.okhttp.streaming.StreamingHttpClientFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object StreamingHttpClientProvider {
    val client: OkHttpClient by lazy {
        StreamingHttpClientFactory.newBuilder()
            .writeTimeout(10, TimeUnit.SECONDS)
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
