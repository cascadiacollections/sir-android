package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [RadioDirectory] backed by the public radio-browser.info API.
 *
 * Follows the API's published guidance: a descriptive `User-Agent`, no single
 * hard-coded server (mirrors come from [mirrorProvider], which discovers them), and a
 * `/json/url/{stationuuid}` report for every user click ([reportClick]).
 *
 * Each call makes at most [retryPolicy]`.maxAttempts` requests, each on the next mirror
 * with exponential backoff between them, all inside [failoverBudgetMs].
 */
class RadioBrowserDirectory(
    private val httpClient: OkHttpClient,
    private val mirrorProvider: MirrorProvider = RotatingMirrorProvider(),
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Wall-clock ceiling on one call's failover across all mirrors. */
    private val failoverBudgetMs: Long = DEFAULT_FAILOVER_BUDGET_MS,
    /** Injectable so the budget can be tested without sleeping. */
    private val nanoTime: () -> Long = System::nanoTime,
    private val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
) : RadioDirectory {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class WireTag(val name: String = "", @SerialName("stationcount") val stationCount: Int = 0)

    override suspend fun search(query: StationQuery): Result<List<Station>> = search(query, StationSearchFilters.NONE)

    override suspend fun search(query: StationQuery, filters: StationSearchFilters): Result<List<Station>> {
        if (query.isBlank) return Result.success(emptyList())
        return fetchStations(query.effectiveLimit, filters) { base ->
            base.addPathSegments("json/stations/search")
                .addQueryParameter("name", query.normalizedText)
                .addPopularityOrder()
                .addFilters(filters.queryParameters())
        }
    }

    override suspend fun topStations(limit: Int): Result<List<Station>> =
        fetchStations(limit, StationSearchFilters.NONE) { base ->
            base.addPathSegments("json/stations/topclick")
        }

    override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> =
        stationsByTag(tag, limit, StationSearchFilters.NONE)

    override suspend fun stationsByTag(tag: String, limit: Int, filters: StationSearchFilters): Result<List<Station>> {
        val normalized = tag.trim()
        if (normalized.isEmpty()) return Result.success(emptyList())
        // radio-browser stores tags lowercase; the genre list capitalizes them for display.
        val tagList = listOfNotNull(normalized, filters.tag)
            .map { it.lowercase(Locale.ROOT) }
            .distinct()
            .joinToString(",")
        return fetchStations(limit, filters) { base ->
            base.addPathSegments("json/stations/search")
                .addQueryParameter("tagList", tagList)
                .addPopularityOrder()
                .addFilters(filters.queryParameters(includeTag = false))
        }
    }

    override suspend fun getStation(id: String): Result<Station?> {
        if (id.isBlank()) return Result.success(null)
        return fetchStations(1, StationSearchFilters.NONE, hideBroken = false) { base ->
            base.addPathSegments("json/stations/byuuid").addPathSegment(id)
        }.map { it.firstOrNull() }
    }

    /**
     * `GET /json/stations/byuuid?uuids=a,b,c`, one request per [RadioDirectory.MAX_BATCH_IDS]
     * ids. Only radio-browser UUIDs are sent; bundled and imported ids are skipped without a
     * request. All batches must succeed, so a caller never mistakes a partial answer for
     * "these stations no longer exist".
     */
    override suspend fun getStations(ids: List<String>): Result<List<Station>> {
        val uuids = ids.filter(StationIds::isRadioBrowserUuid).distinct()
        if (uuids.isEmpty()) return Result.success(emptyList())
        val stations = mutableListOf<Station>()
        for (batch in uuids.chunked(RadioDirectory.MAX_BATCH_IDS)) {
            fetchStations(batch.size, StationSearchFilters.NONE, hideBroken = false) { base ->
                base.addPathSegments("json/stations/byuuid")
                    .addQueryParameter("uuids", batch.joinToString(","))
            }.fold(onSuccess = { stations += it }, onFailure = { return Result.failure(it) })
        }
        return Result.success(stations)
    }

    override suspend fun topTags(limit: Int): Result<List<Tag>> {
        val clamped = limit.coerceIn(1, MAX_TAG_LIMIT)
        return request(
            buildUrl = { base ->
                base.addPathSegments("json/tags")
                    .addQueryParameter("order", "stationcount")
                    .addQueryParameter("reverse", "true")
                    .addQueryParameter("hidebroken", "true")
                    .addQueryParameter("limit", clamped.toString())
            },
            parse = { body ->
                json.decodeFromString<List<WireTag>>(body).mapNotNull { wire ->
                    wire.name.trim().takeIf { it.isNotEmpty() }?.let { Tag(it, wire.stationCount) }
                }
            }
        )
    }

    override suspend fun reportClick(stationId: String): Result<Unit> {
        if (!StationIds.isRadioBrowserUuid(stationId)) return Result.success(Unit)
        return request(
            buildUrl = { base -> base.addPathSegments("json/url").addPathSegment(stationId) },
            // The body describes the station's stream; only the side effect matters.
            parse = { },
            // A timeout or most error statuses may arrive after the mirror already counted
            // the click, and mirrors share counts, so only a request no server handled — no
            // connection, or a 503/429 refusal — is retried, or one tap could count twice.
            isRetryable = { it.isUnhandledRequest() }
        )
    }

    /**
     * [hideBroken] is for listings only: a lookup by UUID is for a station the user already
     * saved or chose, and a station that is failing radio-browser's checks right now must
     * still be found rather than look deleted.
     */
    private suspend fun fetchStations(
        limit: Int,
        filters: StationSearchFilters,
        hideBroken: Boolean = true,
        buildPath: (HttpUrl.Builder) -> HttpUrl.Builder
    ): Result<List<Station>> {
        val clampedLimit = limit.coerceIn(1, StationQuery.MAX_LIMIT)
        return request(
            buildUrl = { base ->
                buildPath(base)
                    .addQueryParameter("limit", clampedLimit.toString())
                    .apply { if (hideBroken) addQueryParameter("hidebroken", "true") }
            },
            parse = { body ->
                val stations = json.decodeFromString<List<Station>>(body)
                    .filter { it.isPlayable }
                    .map { it.normalizedFromDirectory() }
                // Mirrors do not all honour every filter parameter, so re-apply locally.
                filters.applyTo(stations).take(clampedLimit)
            }
        )
    }

    /**
     * One logical request with bounded mirror failover.
     *
     * Only transport failures and retryable statuses move on to the next mirror. A
     * decode error or a plain 4xx is a property of the request, so it would fail
     * identically everywhere and retrying would only multiply the wait.
     */
    private suspend fun <T> request(
        buildUrl: (HttpUrl.Builder) -> HttpUrl.Builder,
        parse: (String) -> T,
        isRetryable: (Throwable?) -> Boolean = { it.isRetryable() }
    ): Result<T> = withContext(ioDispatcher) {
        val deadline = nanoTime() + failoverBudgetMs * NANOS_PER_MILLI
        val mirrors = mirrorProvider.mirrors()
            .mapNotNull { it.toHttpUrlOrNull() }
            .ifEmpty {
                return@withContext Result.failure(IOException("No usable radio-browser mirror"))
            }
        val attempts = retryPolicy.maxAttempts
        var lastFailure: Throwable? = null

        for (attempt in 0 until attempts) {
            // `execute()` blocks and never observes cancellation, so without this a
            // search kept issuing requests after the user left the screen.
            ensureActive()

            val url = buildUrl(mirrors[attempt % mirrors.size].newBuilder()).build()
            val result = runCatching { parse(fetch(url)) }
            result.onSuccess { return@withContext Result.success(it) }

            val failure = result.exceptionOrNull()
            lastFailure = failure
            if (!isRetryable(failure)) break
            if (attempt == attempts - 1) break

            // Each attempt carries its own callTimeout, so a run of slow mirrors could
            // otherwise hold the search spinner for the sum of all of them.
            val backoff = retryPolicy.delayAfter(attempt)
            if (nanoTime() + backoff * NANOS_PER_MILLI >= deadline) break
            delay(backoff)
        }

        Result.failure(lastFailure ?: IOException("No usable radio-browser mirror"))
    }

    private fun Throwable?.isRetryable(): Boolean = when (this) {
        is HttpStatusException -> isRetryable
        is IOException -> true
        else -> false
    }

    /** No server processed the request: it never connected, or was refused outright. */
    private fun Throwable?.isUnhandledRequest(): Boolean = when (this) {
        is ConnectException, is UnknownHostException, is NoRouteToHostException -> true
        is HttpStatusException -> code == 503 || code == 429
        else -> false
    }

    private fun fetch(url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body.string()
            // "No results" is `[]`, not an empty body. A blank body is a malfunctioning
            // mirror, so fail over rather than reporting — and caching — no results.
            if (body.isBlank()) throw IOException("radio-browser returned an empty body")
            return body
        }
    }

    private fun HttpUrl.Builder.addPopularityOrder(): HttpUrl.Builder =
        addQueryParameter("order", "clickcount").addQueryParameter("reverse", "true")

    private fun HttpUrl.Builder.addFilters(params: List<Pair<String, String>>): HttpUrl.Builder =
        apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }

    companion object {
        /**
         * radio-browser asks for a speaking agent identifying the app; it also
         * rate-limits by agent, so this must not be shared with other clients.
         */
        const val DEFAULT_USER_AGENT: String = "SIR-Android/1.0 (+https://github.com/cascadiacollections/sir-android)"

        /**
         * Roughly one and a half attempts at the 20s `callTimeout` the directory client
         * uses — long enough for a slow mirror to answer, short enough that a search box
         * never spins for the sum of every mirror's timeout.
         */
        const val DEFAULT_FAILOVER_BUDGET_MS: Long = 30_000

        /** Upper bound for [topTags]; the genre list never needs more. */
        const val MAX_TAG_LIMIT: Int = 500

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
