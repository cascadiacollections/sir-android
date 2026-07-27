package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Wall-clock ceiling on one call's failover across all mirrors. */
    private val failoverBudgetMs: Long = DEFAULT_FAILOVER_BUDGET_MS,
    /** Injectable so the budget can be tested without sleeping. */
    private val nanoTime: () -> Long = System::nanoTime
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
        val deadline = nanoTime() + failoverBudgetMs * 1_000_000
        var lastFailure: Throwable? = null

        for (mirror in mirrorProvider.mirrors()) {
            // `execute()` blocks and never observes cancellation, so without this a
            // search kept issuing requests after the user left the screen.
            ensureActive()

            val base = mirror.toHttpUrlOrNull()?.newBuilder() ?: continue
            val url = buildPath(base)
                .addQueryParameter("limit", clampedLimit.toString())
                .addQueryParameter("hidebroken", "true")
                .build()

            val attempt = runCatching { fetch(url) }
            attempt.onSuccess { return@withContext Result.success(it) }

            val failure = attempt.exceptionOrNull()
            lastFailure = failure

            // Only transport failures are worth another mirror. A decode error or a 4xx
            // is a property of the request, so it will fail identically everywhere —
            // trying all four just multiplied the wait by four before showing the error.
            if (failure !is IOException) break

            // Each attempt carries its own callTimeout, so a run of slow mirrors could
            // otherwise hold the search spinner for the sum of all of them.
            if (nanoTime() >= deadline) break
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
            // "No results" is `[]`, not an empty body. A blank body is a malfunctioning
            // mirror, so fail over rather than reporting — and caching — no results.
            if (body.isBlank()) throw IOException("radio-browser returned an empty body")
            return json.decodeFromString<List<Station>>(body).filter { it.isPlayable }
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT: String = "SIR-Android/1.0 (+https://github.com/cascadiacollections/sir-android)"

        /**
         * Roughly one and a half attempts at the 20s `callTimeout` the directory client
         * uses — long enough for a slow mirror to answer, short enough that a search box
         * never spins for the sum of every mirror's timeout.
         */
        const val DEFAULT_FAILOVER_BUDGET_MS: Long = 30_000
    }
}
