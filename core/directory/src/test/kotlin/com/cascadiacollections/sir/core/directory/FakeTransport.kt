package com.cascadiacollections.sir.core.directory

import okhttp3.HttpUrl
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
    data class Http(val code: Int = 200, val body: String = "[]") : Reply
    data class Fail(val error: IOException = IOException("unreachable")) : Reply
}

/**
 * In-process HTTP transport: an application interceptor that answers every request
 * without touching the network, recording what was sent.
 */
class FakeTransport(var handler: (HttpUrl) -> Reply = { Reply.Http() }) : Interceptor {

    val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())

    val urls: List<HttpUrl> get() = requests.map { it.url }

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        return when (val reply = handler(request.url)) {
            is Reply.Fail -> throw reply.error
            is Reply.Http -> Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(reply.code)
                .message("fake")
                .body(reply.body.toResponseBody())
                .build()
        }
    }
}
