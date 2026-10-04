package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSuccess
import assertk.assertions.prop
import com.cascadiacollections.sir.core.model.StationQuery
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Test

class RadioBrowserDirectoryTest {

    private val mirrors = listOf(
        "https://m1.example",
        "https://m2.example",
        "https://m3.example",
        "https://m4.example"
    )

    private val uuid = "96062a7b-0601-11e8-ae97-52543be04c81"

    private fun stationJson(
        id: String = uuid,
        name: String = "Jazz FM",
        bitrate: Int = 128,
        country: String = "GB",
        tags: String = "jazz,smooth jazz",
        favicon: String = ""
    ) = """{"stationuuid":"$id","name":"$name","url":"https://s.example/$id","favicon":"$favicon",""" +
        """"bitrate":$bitrate,"codec":"MP3","countrycode":"$country","tags":"$tags","extra":1}"""

    private fun TestScope.directory(
        transport: FakeTransport,
        mirrorList: List<String> = mirrors,
        budgetMs: Long = RadioBrowserDirectory.DEFAULT_FAILOVER_BUDGET_MS,
        nanoTime: (() -> Long)? = null,
        retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
    ) = RadioBrowserDirectory(
        httpClient = transport.client,
        mirrorProvider = RotatingMirrorProvider(mirrorList, shuffle = { it }),
        ioDispatcher = StandardTestDispatcher(testScheduler),
        failoverBudgetMs = budgetMs,
        nanoTime = nanoTime ?: { testScheduler.currentTime * 1_000_000 },
        retryPolicy = retryPolicy
    )

    // region search parameters

    @Test
    fun `name search orders by click count and hides broken stations`() = runTest {
        val transport = FakeTransport()
        directory(transport).search(StationQuery("  jazz  ")).getOrThrow()

        val url = transport.urls.single()
        assertThat(url.encodedPath).isEqualTo("/json/stations/search")
        assertThat(url.queryParameter("name")).isEqualTo("jazz")
        assertThat(url.queryParameter("order")).isEqualTo("clickcount")
        assertThat(url.queryParameter("reverse")).isEqualTo("true")
        assertThat(url.queryParameter("hidebroken")).isEqualTo("true")
        assertThat(url.queryParameter("limit")).isEqualTo("40")
        assertThat(url.queryParameter("tagList")).isNull()
    }

    @Test
    fun `search defaults to 40 results`() {
        assertThat(StationQuery("x").effectiveLimit).isEqualTo(40)
    }

    @Test
    fun `requests identify the app with a descriptive user agent`() = runTest {
        val transport = FakeTransport()
        directory(transport).topStations(5)

        val agent = transport.requests.single().header("User-Agent").orEmpty()
        assertThat(agent).isEqualTo(RadioBrowserDirectory.DEFAULT_USER_AGENT)
        assertThat(agent).contains("github.com/cascadiacollections/sir-android")
    }

    @Test
    fun `blank search makes no request`() = runTest {
        val transport = FakeTransport()
        assertThat(directory(transport).search(StationQuery("  ")).getOrThrow()).isEmpty()
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `filters become radio-browser parameters`() = runTest {
        val transport = FakeTransport()
        val filters = StationSearchFilters(
            bitrateMinKbps = 64,
            bitrateMaxKbps = 320,
            tag = " Jazz ",
            countryCode = "gb"
        )
        directory(transport).search(StationQuery("fm"), filters).getOrThrow()

        val url = transport.urls.single()
        assertThat(url.queryParameter("bitrateMin")).isEqualTo("64")
        assertThat(url.queryParameter("bitrateMax")).isEqualTo("320")
        assertThat(url.queryParameter("tagList")).isEqualTo("jazz")
        assertThat(url.queryParameter("countrycode")).isEqualTo("GB")
    }

    @Test
    fun `filters are re-applied locally and missing values still match`() = runTest {
        val body = "[" + listOf(
            stationJson(id = "a", name = "Low", bitrate = 32),
            stationJson(id = "b", name = "Unknown Bitrate", bitrate = 0),
            stationJson(id = "c", name = "Wrong Country", country = "US"),
            stationJson(id = "d", name = "No Country", country = ""),
            stationJson(id = "e", name = "Right", bitrate = 128)
        ).joinToString(",") + "]"
        val transport = FakeTransport { Reply.Http(body = body) }

        val names = directory(transport)
            .search(StationQuery("x"), StationSearchFilters(bitrateMinKbps = 64, countryCode = "GB"))
            .getOrThrow()
            .map { it.name }

        assertThat(names).containsExactly("Unknown Bitrate", "No Country", "Right")
    }

    @Test
    fun `genre browse uses a lowercase tagList and folds in the filter tag`() = runTest {
        val transport = FakeTransport()
        val directory = directory(transport)

        directory.stationsByTag("Hip Hop", 10).getOrThrow()
        directory.stationsByTag("Jazz", 10, StationSearchFilters(tag = "Smooth", countryCode = "us")).getOrThrow()

        val plain = transport.urls[0]
        assertThat(plain.encodedPath).isEqualTo("/json/stations/search")
        assertThat(plain.queryParameter("tagList")).isEqualTo("hip hop")
        assertThat(plain.queryParameter("tag")).isNull()
        assertThat(plain.queryParameter("order")).isEqualTo("clickcount")
        assertThat(plain.queryParameter("reverse")).isEqualTo("true")
        assertThat(plain.queryParameter("limit")).isEqualTo("10")

        val filtered = transport.urls[1]
        assertThat(filtered.queryParameterValues("tagList")).containsExactly("jazz,smooth")
        assertThat(filtered.queryParameter("countrycode")).isEqualTo("US")
    }

    @Test
    fun `top stations still use topclick`() = runTest {
        val transport = FakeTransport()
        directory(transport).topStations(500).getOrThrow()

        val url = transport.urls.single()
        assertThat(url.encodedPath).isEqualTo("/json/stations/topclick")
        assertThat(url.queryParameter("limit")).isEqualTo(StationQuery.MAX_LIMIT.toString())
    }

    // endregion

    // region mapping

    @Test
    fun `station names and favicons are cleaned when mapped`() = runTest {
        val body = "[" + stationJson(
            name = "Jazz_FM [HD] (128k)",
            favicon = "http://img.example:8080/logo.png"
        ) + "]"
        val transport = FakeTransport { Reply.Http(body = body) }

        val station = directory(transport).topStations(1).getOrThrow().single()

        assertThat(station.name).isEqualTo("Jazz FM")
        assertThat(station.favicon).isEqualTo("https://img.example/logo.png")
        assertThat(station.id).isEqualTo(uuid)
    }

    @Test
    fun `unplayable stations are dropped`() = runTest {
        val body = """[{"stationuuid":"x","name":"No URL","url":""}]"""
        val transport = FakeTransport { Reply.Http(body = body) }

        assertThat(directory(transport).topStations(5).getOrThrow()).isEmpty()
    }

    // endregion

    // region tags

    @Test
    fun `top tags request the station-count ranking`() = runTest {
        val transport = FakeTransport()
        directory(transport).topTags().getOrThrow()

        val url = transport.urls.single()
        assertThat(url.encodedPath).isEqualTo("/json/tags")
        assertThat(url.queryParameter("order")).isEqualTo("stationcount")
        assertThat(url.queryParameter("reverse")).isEqualTo("true")
        assertThat(url.queryParameter("hidebroken")).isEqualTo("true")
        assertThat(url.queryParameter("limit")).isEqualTo("48")
    }

    @Test
    fun `top tags parse names and counts and drop blank names`() = runTest {
        val body = """[{"name":"pop","stationcount":5321},{"name":"  ","stationcount":9},""" +
            """{"name":"hip hop","stationcount":800,"other":true}]"""
        val transport = FakeTransport { Reply.Http(body = body) }

        val tags = directory(transport).topTags(10).getOrThrow()

        assertThat(tags).containsExactly(Tag("pop", 5321), Tag("hip hop", 800))
        assertThat(tags.map { it.displayName }).containsExactly("Pop", "Hip Hop")
    }

    // endregion

    // region click reporting

    @Test
    fun `click is reported to the url endpoint for the station uuid`() = runTest {
        val transport = FakeTransport { Reply.Http(body = """{"ok":true}""") }

        assertThat(directory(transport).reportClick(uuid)).isSuccess()

        val request = transport.requests.single()
        assertThat(request.url.encodedPath).isEqualTo("/json/url/$uuid")
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.queryParameter("limit")).isNull()
        assertThat(request.url.queryParameter("hidebroken")).isNull()
    }

    @Test
    fun `clicks for non radio-browser ids make no request`() = runTest {
        val transport = FakeTransport()
        val directory = directory(transport)

        listOf("imported:https://example.com/stream", "sir-default", "curated-worldwide-fm", "", "not-a-uuid")
            .forEach { assertThat(directory.reportClick(it)).isSuccess() }

        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `click reporting fails over when no server handled the request`() = runTest {
        listOf<Reply>(Reply.Fail(ConnectException("refused")), Reply.Http(code = 503)).forEach { failure ->
            val transport = FakeTransport { url ->
                if (url.host == "m1.example") failure else Reply.Http(body = "{}")
            }

            assertThat(directory(transport).reportClick(uuid)).isSuccess()
            assertThat(transport.urls.map { it.host }).containsExactly("m1.example", "m2.example")
        }
    }

    @Test
    fun `click reporting does not retry a request a server may have counted`() = runTest {
        listOf<Reply>(Reply.Fail(SocketTimeoutException("read")), Reply.Http(code = 502)).forEach { failure ->
            val transport = FakeTransport { url ->
                if (url.host == "m1.example") failure else Reply.Http(body = "{}")
            }

            assertThat(directory(transport).reportClick(uuid)).isFailure()
            assertThat(transport.urls.map { it.host }).containsExactly("m1.example")
        }
    }

    @Test
    fun `uuid lookups include stations that are currently failing checks`() = runTest {
        val transport = FakeTransport { Reply.Http(body = "[" + stationJson() + "]") }
        val directory = directory(transport)

        directory.getStation(uuid).getOrThrow()
        directory.getStations(listOf(uuid)).getOrThrow()

        assertThat(transport.urls).hasSize(2)
        transport.urls.forEach { assertThat(it.queryParameter("hidebroken")).isNull() }
    }

    // endregion

    // region failover

    @Test
    fun `transport failure fails over to the next mirror`() = runTest {
        val transport = FakeTransport { url ->
            if (url.host == "m1.example") Reply.Fail() else Reply.Http(body = "[" + stationJson() + "]")
        }

        val stations = directory(transport).topStations(5).getOrThrow()

        assertThat(stations).hasSize(1)
        assertThat(transport.urls.map { it.host }).containsExactly("m1.example", "m2.example")
    }

    @Test
    fun `server errors fail over but client errors do not`() = runTest {
        val serverError = FakeTransport { url ->
            if (url.host == "m1.example") Reply.Http(code = 503) else Reply.Http()
        }
        assertThat(directory(serverError).topStations(5)).isSuccess()
        assertThat(serverError.requests).hasSize(2)

        val clientError = FakeTransport { Reply.Http(code = 404) }
        val result = directory(clientError).topStations(5)
        assertThat(result)
            .isFailure()
            .isInstanceOf<HttpStatusException>()
            .prop(HttpStatusException::code)
            .isEqualTo(404)
        assertThat(clientError.requests).hasSize(1)
    }

    @Test
    fun `decode errors are not retried`() = runTest {
        val transport = FakeTransport { Reply.Http(body = "{not json") }

        val result = directory(transport).topStations(5)

        assertThat(result).isFailure().isInstanceOf<SerializationException>()
        assertThat(transport.requests).hasSize(1)
    }

    @Test
    fun `a blank body is treated as a broken mirror`() = runTest {
        val transport = FakeTransport { url ->
            if (url.host == "m1.example") Reply.Http(body = " ") else Reply.Http(body = "[]")
        }

        assertThat(directory(transport).topStations(5)).isSuccess()
        assertThat(transport.requests).hasSize(2)
    }

    @Test
    fun `attempts are bounded with exponential backoff`() = runTest {
        val transport = FakeTransport { Reply.Fail() }

        val result = directory(transport).search(StationQuery("x"))

        assertThat(result).isFailure().isInstanceOf<IOException>()
        assertThat(transport.urls.map { it.host })
            .containsExactly("m1.example", "m2.example", "m3.example")
        // 350 ms after the first failure, 700 ms after the second, none after the last.
        assertThat(testScheduler.currentTime).isEqualTo(350L + 700L)
    }

    @Test
    fun `attempts wrap around a short mirror list`() = runTest {
        val transport = FakeTransport { Reply.Fail() }

        directory(transport, mirrorList = listOf("https://only.example")).topStations(5)

        assertThat(transport.requests).hasSize(3)
    }

    @Test
    fun `the failover budget stops retries before backoff would exceed it`() = runTest {
        val transport = FakeTransport { Reply.Fail() }

        directory(transport, budgetMs = 500).topStations(5)

        // After the first failure 350 ms fits; after the second, 350 + 700 does not.
        assertThat(transport.requests).hasSize(2)
    }

    @Test
    fun `a slow mirror run cannot exceed the budget`() = runTest {
        var now = 0L
        val transport = FakeTransport {
            now += 40_000L * 1_000_000 // every attempt "takes" 40 s
            Reply.Fail()
        }

        directory(transport, nanoTime = { now }).topStations(5)

        assertThat(transport.requests).hasSize(1)
    }

    @Test
    fun `no usable mirror is a failure`() = runTest {
        val transport = FakeTransport()

        val result = directory(transport, mirrorList = listOf("not a url")).topStations(5)

        assertThat(result).isFailure()
        assertThat(transport.requests).isEmpty()
    }

    // endregion
}
