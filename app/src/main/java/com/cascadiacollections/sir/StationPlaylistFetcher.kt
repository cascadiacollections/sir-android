package com.cascadiacollections.sir

import android.util.Log
import com.cascadiacollections.sir.core.playback.StreamEndpoint
import com.cascadiacollections.sir.core.playback.StreamEndpoints
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/**
 * Follows a station URL that is itself a `.pls`/`.m3u` playlist to the stream it names.
 *
 * Built on the streaming client so it shares its connection pool and DNS cache — the
 * stream host is usually the playlist host, so the connection is often reused — but with
 * a call timeout, because the streaming client has none and a playlist is a one-shot
 * fetch. The body is capped so a server that answers a `.m3u` path with endless audio
 * cannot be read forever.
 */
class StationPlaylistFetcher(streamingClient: OkHttpClient, private val userAgent: String) {

    private val client = streamingClient.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** The endpoint to play for playlist [url]; the original [url] whenever that fails. */
    suspend fun resolve(url: String): StreamEndpoint = StreamEndpoints.fromPlaylist(url, fetch(url)).also {
        Log.d(TAG, "Resolved playlist $url -> ${it.url}${if (it.isHls) " (HLS)" else ""}")
    }

    private suspend fun fetch(url: String): String? = runInterruptible(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Playlist fetch failed: HTTP ${response.code} for $url")
                    return@use null
                }
                val body = response.body
                val source = body.source()
                val buffer = Buffer()
                while (buffer.size < MAX_BODY_BYTES) {
                    if (source.read(buffer, MAX_BODY_BYTES - buffer.size) == -1L) break
                }
                buffer.readString(body.contentType()?.charset() ?: Charsets.UTF_8)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Playlist fetch failed for $url", e)
            null
        } catch (e: IllegalArgumentException) {
            // Request.Builder.url() rejects anything that isn't an http(s) URL.
            Log.w(TAG, "Not a fetchable playlist URL: $url", e)
            null
        } catch (e: SecurityException) {
            // A network security policy refusal; play the original URL and let the player
            // report it through the normal failure path.
            Log.w(TAG, "Playlist fetch refused for $url", e)
            null
        }
    }

    private companion object {
        const val TAG = "StationPlaylistFetcher"
        const val TIMEOUT_SECONDS = 10L
        const val MAX_BODY_BYTES = 64L * 1024
    }
}
