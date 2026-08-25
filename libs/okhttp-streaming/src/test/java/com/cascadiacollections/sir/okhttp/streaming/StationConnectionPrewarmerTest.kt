package com.cascadiacollections.sir.okhttp.streaming

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class StationConnectionPrewarmerTest {

    @Test
    fun `prewarms at most two distinct hosts with HEAD requests`() {
        val requests = mutableListOf<String>()
        val client = testClient { request ->
            requests += "${request.method} ${request.url.host}"
        }

        StationConnectionPrewarmer(client, isPowerSaveMode = { false }, execute = { it() })
            .prewarm(
                listOf(
                    "https://first.example/live",
                    "https://first.example/other",
                    "https://second.example/live",
                    "https://third.example/live",
                )
            )

        assertEquals(listOf("HEAD first.example", "HEAD second.example"), requests)
    }

    @Test
    fun `does not prewarm in power save mode`() {
        val requests = mutableListOf<String>()
        val client = testClient { request -> requests += request.url.host }

        StationConnectionPrewarmer(client, isPowerSaveMode = { true }, execute = { it() })
            .prewarm(listOf("https://first.example/live"))

        assertEquals(emptyList<String>(), requests)
    }

    private fun testClient(onRequest: (okhttp3.Request) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                onRequest(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
}
