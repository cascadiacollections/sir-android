package com.cascadiacollections.sir.core.artwork

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Finds cover art for a track through the iTunes Search API (ShoutKit's `AlbumArtLookup`).
 *
 * Answers are memoised per [TrackKey] in an LRU of [cacheSize] entries — misses included,
 * so a song iTunes doesn't know is asked about once rather than every time a station
 * re-announces it. A transport failure, an HTTP error or an unreadable body is *not*
 * cached: none of them says anything about the song, and the next announcement retries.
 */
class AlbumArtLookup(
    private val client: OkHttpClient = defaultHttpClient(),
    private val userAgent: String = DEFAULT_USER_AGENT,
    cacheSize: Int = DEFAULT_CACHE_SIZE,
) {

    /** A cached answer; [art] is null for a definitive "no artwork for this song". */
    private data class Entry(val art: AlbumArt?)

    private val cache = LruCache<TrackKey, Entry>(cacheSize)

    /**
     * Artwork for [artist]'s [title] in [country]'s storefront, or null when there is none,
     * either half is blank, or the lookup failed.
     */
    suspend fun lookup(artist: String?, title: String?, country: String?): AlbumArt? {
        val key = TrackKey.of(artist, title) ?: return null
        cache[key]?.let { return it.art }
        val url = ITunesSearch.searchUrl(artist.orEmpty(), title.orEmpty(), country)
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        val body = client.newCall(request).awaitBody() ?: return null
        val art = try {
            ITunesSearch.parse(body)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        cache[key] = Entry(art)
        return art
    }

    /** Whether an answer (hit or miss) for the pair is cached. Exposed for tests. */
    fun isCached(artist: String?, title: String?): Boolean =
        TrackKey.of(artist, title)?.let(cache::contains) == true

    /**
     * The response body of a successful call, or null on any failure. Enqueued rather than
     * executed so cancelling the coroutine — the track changed again — cancels the request.
     */
    private suspend fun Call.awaitBody(): String? = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.use {
                    try {
                        if (it.isSuccessful) it.body.string() else null
                    } catch (_: IOException) {
                        null
                    }
                }
                continuation.resume(body?.takeIf { it.isNotBlank() })
            }
        })
    }

    companion object {
        const val DEFAULT_CACHE_SIZE = 128
        const val TIMEOUT_SECONDS = 8L
        const val DEFAULT_USER_AGENT: String =
            "SIR-Android/1.0 (+https://github.com/cascadiacollections/sir-android)"

        /** A client whose whole call, connect included, is bounded to [TIMEOUT_SECONDS]. */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
