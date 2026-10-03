package com.cascadiacollections.sir.core.artwork

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.Collections

/** What the fake network answers for one request. */
sealed interface Reply {
    data class Http(val code: Int = 200, val body: String = """{"resultCount":0,"results":[]}""") : Reply
    data class Fail(val error: IOException = IOException("unreachable")) : Reply
}

/** In-process transport: answers every request without touching the network, recording it. */
class FakeTransport(var reply: Reply = Reply.Http()) : Interceptor {

    val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        return when (val r = reply) {
            is Reply.Fail -> throw r.error
            is Reply.Http -> Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(r.code)
                .message("fake")
                .body(r.body.toResponseBody())
                .build()
        }
    }
}
