package com.cascadiacollections.sir.core.directory

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Supplies radio-browser.info API mirrors.
 *
 * radio-browser has no single stable host: clients are expected to spread load across
 * mirrors and fail over when one is unreachable. Extracting this lets the HTTP client
 * stay dumb and keeps mirror policy unit-testable.
 *
 * `suspend` because the production provider discovers the live mirror set over the
 * network; callers already run on an IO dispatcher.
 */
interface MirrorProvider {
    /** Base URLs to try, in preference order, for a single logical request. */
    suspend fun mirrors(): List<String>
}

/**
 * Rotates over a fixed mirror list so load is spread across app launches while each
 * individual request still has deterministic failover order.
 */
class RotatingMirrorProvider(
    private val mirrors: List<String> = DEFAULT_MIRRORS,
    private val shuffle: (List<String>) -> List<String> = { it.shuffled() }
) : MirrorProvider {

    init {
        require(mirrors.isNotEmpty()) { "At least one mirror is required" }
    }

    override suspend fun mirrors(): List<String> = shuffle(mirrors)

    companion object {
        /** DNS round-robin entry point; a valid mirror in its own right. */
        const val ALL_MIRRORS_HOST: String = "https://all.api.radio-browser.info"

        /**
         * Known radio-browser.info mirrors, used only when live discovery fails. The
         * API guidance is to never hard-code a single server, so this is a fallback
         * list, and `all.api.radio-browser.info` is kept on it because it resolves to
         * whichever mirrors are currently healthy.
         */
        val DEFAULT_MIRRORS: List<String> = listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            ALL_MIRRORS_HOST
        )
    }
}

/**
 * Source of the live radio-browser server names (e.g. `de1.api.radio-browser.info`).
 *
 * Blocking by contract — it is only ever invoked from an IO dispatcher — and a
 * `fun interface` so tests can supply a lambda.
 */
fun interface ServerListSource {
    /** Server host names; throws on transport failure. */
    @Throws(IOException::class)
    fun serverNames(): List<String>
}

/**
 * Fetches `https://all.api.radio-browser.info/json/servers`, the discovery endpoint the
 * radio-browser documentation recommends over hard-coding hosts.
 *
 * Uses a short `callTimeout` derived from [httpClient] so a dead discovery endpoint costs
 * a few seconds of the caller's failover budget, not all of it.
 */
class RadioBrowserServerListSource(
    httpClient: OkHttpClient,
    private val userAgent: String = RadioBrowserDirectory.DEFAULT_USER_AGENT,
    private val discoveryUrl: String = "$DEFAULT_DISCOVERY_HOST/json/servers",
    discoveryTimeoutMs: Long = DEFAULT_DISCOVERY_TIMEOUT_MS
) : ServerListSource {

    private val client: OkHttpClient = httpClient.newBuilder()
        .callTimeout(discoveryTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ServerEntry(val name: String = "")

    override fun serverNames(): List<String> {
        val request = Request.Builder()
            .url(discoveryUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body.string()
            if (body.isBlank()) throw IOException("radio-browser server list was empty")
            return runCatching { json.decodeFromString<List<ServerEntry>>(body) }
                .getOrElse { throw IOException("radio-browser server list was malformed", it) }
                .map { it.name }
        }
    }

    companion object {
        const val DEFAULT_DISCOVERY_HOST: String = RotatingMirrorProvider.ALL_MIRRORS_HOST
        const val DEFAULT_DISCOVERY_TIMEOUT_MS: Long = 5_000
    }
}

/**
 * Discovers the live mirror set once per [ttlMillis] (24h by default), shuffles it per
 * request so load spreads across mirrors, and falls back to [fallback] when discovery
 * fails or returns nothing usable.
 *
 * The discovered set is cached per process; a failed discovery is remembered for
 * [failureRetryMillis] so an unreachable discovery endpoint is not re-tried (and waited
 * on) before every single request. `all.api.radio-browser.info` is always appended as
 * the last resort so even a stale discovered list can still reach a healthy mirror.
 */
class DiscoveringMirrorProvider(
    private val source: ServerListSource,
    private val fallback: List<String> = RotatingMirrorProvider.DEFAULT_MIRRORS,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val failureRetryMillis: Long = DEFAULT_FAILURE_RETRY_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val shuffle: (List<String>) -> List<String> = { it.shuffled() }
) : MirrorProvider {

    init {
        require(fallback.isNotEmpty()) { "At least one fallback mirror is required" }
    }

    private class Snapshot(val mirrors: List<String>, val expiresAt: Long)

    private val mutex = Mutex()
    private var snapshot: Snapshot? = null

    override suspend fun mirrors(): List<String> {
        val current = mutex.withLock {
            val cached = snapshot
            if (cached != null && clock() < cached.expiresAt) {
                cached.mirrors
            } else {
                discover().also { snapshot = it }.mirrors
            }
        }
        return withLastResort(shuffle(current.filterNot { it == LAST_RESORT }))
    }

    private fun discover(): Snapshot {
        val discovered = runCatching { source.serverNames() }
            .getOrDefault(emptyList())
            .mapNotNull(::toBaseUrl)
            .distinct()
        return if (discovered.isNotEmpty()) {
            Snapshot(discovered, clock() + ttlMillis)
        } else {
            Snapshot(fallback, clock() + failureRetryMillis)
        }
    }

    private fun withLastResort(mirrors: List<String>): List<String> = mirrors + LAST_RESORT

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 24 * 60 * 60 * 1000L
        const val DEFAULT_FAILURE_RETRY_MILLIS: Long = 10 * 60 * 1000L

        private const val LAST_RESORT = RotatingMirrorProvider.ALL_MIRRORS_HOST
        private val HOST_NAME = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

        /**
         * Turns a discovered server name into an https base URL, rejecting anything that
         * is not a plain host name so a malformed entry can never redirect requests.
         */
        internal fun toBaseUrl(name: String): String? {
            val host = name.trim().trimEnd('.').lowercase(Locale.ROOT)
            return if (HOST_NAME.matches(host)) "https://$host" else null
        }
    }
}
