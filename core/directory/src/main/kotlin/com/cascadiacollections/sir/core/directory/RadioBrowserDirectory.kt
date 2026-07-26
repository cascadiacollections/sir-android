package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * [RadioDirectory] backed by the public radio-browser.info API.
 *
 * No authentication is required; the API asks clients to identify themselves via
 * `User-Agent` and stay under ~100 requests/minute. Requests fail over across the
 * mirrors returned by [mirrorProvider] before surfacing an error.
 */
class RadioBrowserDirectory(
    private val httpClient: OkHttpClient,
    private val mirrorProvider: MirrorProvider = RotatingMirrorProvider(),
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RadioDirectory {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: StationQuery): Result<List<Station>> {
        if (query.isBlank) return Result.success(emptyList())
        return get(query.effectiveLimit) { base ->
            base.addPathSegments("json/stations/search")
                .addQueryParameter("name", query.normalizedText)
        }
    }

    override suspend fun topStations(limit: Int): Result<List<Station>> =
        get(limit) { base -> base.addPathSegments("json/stations/topclick") }

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> {
        val normalized = tag.trim()
        if (normalized.isEmpty()) return Result.success(emptyList())
        return get(limit) { base ->
            base.addPathSegments("json/stations/search")
                .addQueryParameter("tag", normalized)
        }
    }

    private suspend fun get(
        limit: Int,
        buildPath: (HttpUrl.Builder) -> HttpUrl.Builder
    ): Result<List<Station>> = withContext(ioDispatcher) {
        val clampedLimit = limit.coerceIn(1, StationQuery.MAX_LIMIT)
        var lastFailure: Throwable? = null

        for (mirror in mirrorProvider.mirrors()) {
            val base = mirror.toHttpUrlOrNull()?.newBuilder() ?: continue
            val url = buildPath(base)
                .addQueryParameter("limit", clampedLimit.toString())
                .addQueryParameter("hidebroken", "true")
                .build()

            val attempt = runCatching { fetch(url) }
            attempt.onSuccess { return@withContext Result.success(it) }
            lastFailure = attempt.exceptionOrNull()
        }

        Result.failure(lastFailure ?: IOException("No usable radio-browser mirror"))
    }

    private fun fetch(url: HttpUrl): List<Station> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("radio-browser responded HTTP ${response.code}")
            }
            val body = response.body.string()
            if (body.isBlank()) return emptyList()
            return json.decodeFromString<List<Station>>(body).filter { it.isPlayable }
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT: String = "SIR-Android/1.0 (+https://github.com/cascadiacollections/sir-android)"
    }
}
