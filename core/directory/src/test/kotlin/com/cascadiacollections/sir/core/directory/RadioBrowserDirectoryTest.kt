package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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
        assertEquals("/json/stations/search", url.encodedPath)
        assertEquals("jazz", url.queryParameter("name"))
        assertEquals("clickcount", url.queryParameter("order"))
        assertEquals("true", url.queryParameter("reverse"))
        assertEquals("true", url.queryParameter("hidebroken"))
        assertEquals("40", url.queryParameter("limit"))
        assertNull(url.queryParameter("tagList"))
    }

    @Test
    fun `search defaults to 40 results`() {
        assertEquals(40, StationQuery("x").effectiveLimit)
    }

    @Test
    fun `requests identify the app with a descriptive user agent`() = runTest {
        val transport = FakeTransport()
        directory(transport).topStations(5)

        val agent = transport.requests.single().header("User-Agent").orEmpty()
        assertEquals(RadioBrowserDirectory.DEFAULT_USER_AGENT, agent)
        assertTrue(agent.contains("github.com/cascadiacollections/sir-android"))
    }

    @Test
    fun `blank search makes no request`() = runTest {
        val transport = FakeTransport()
        assertEquals(emptyList<Any>(), directory(transport).search(StationQuery("  ")).getOrThrow())
        assertTrue(transport.requests.isEmpty())
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
        assertEquals("64", url.queryParameter("bitrateMin"))
        assertEquals("320", url.queryParameter("bitrateMax"))
        assertEquals("jazz", url.queryParameter("tagList"))
        assertEquals("GB", url.queryParameter("countrycode"))
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

        assertEquals(listOf("Unknown Bitrate", "No Country", "Right"), names)
    }

    @Test
    fun `genre browse uses a lowercase tagList and folds in the filter tag`() = runTest {
        val transport = FakeTransport()
        val directory = directory(transport)

        directory.stationsByTag("Hip Hop", 10).getOrThrow()
        directory.stationsByTag("Jazz", 10, StationSearchFilters(tag = "Smooth", countryCode = "us")).getOrThrow()

        val plain = transport.urls[0]
        assertEquals("/json/stations/search", plain.encodedPath)
        assertEquals("hip hop", plain.queryParameter("tagList"))
        assertNull(plain.queryParameter("tag"))
        assertEquals("clickcount", plain.queryParameter("order"))
        assertEquals("true", plain.queryParameter("reverse"))
        assertEquals("10", plain.queryParameter("limit"))

        val filtered = transport.urls[1]
        assertEquals(listOf("jazz,smooth"), filtered.queryParameterValues("tagList"))
        assertEquals("US", filtered.queryParameter("countrycode"))
    }

    @Test
    fun `top stations still use topclick`() = runTest {
        val transport = FakeTransport()
        directory(transport).topStations(500).getOrThrow()

        val url = transport.urls.single()
        assertEquals("/json/stations/topclick", url.encodedPath)
        assertEquals(StationQuery.MAX_LIMIT.toString(), url.queryParameter("limit"))
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

        assertEquals("Jazz FM", station.name)
        assertEquals("https://img.example/logo.png", station.favicon)
        assertEquals(uuid, station.id)
    }

    @Test
    fun `unplayable stations are dropped`() = runTest {
        val body = """[{"stationuuid":"x","name":"No URL","url":""}]"""
        val transport = FakeTransport { Reply.Http(body = body) }

        assertTrue(directory(transport).topStations(5).getOrThrow().isEmpty())
    }

    // endregion

    // region tags

    @Test
    fun `top tags request the station-count ranking`() = runTest {
        val transport = FakeTransport()
        directory(transport).topTags().getOrThrow()

        val url = transport.urls.single()
        assertEquals("/json/tags", url.encodedPath)
        assertEquals("stationcount", url.queryParameter("order"))
        assertEquals("true", url.queryParameter("reverse"))
        assertEquals("true", url.queryParameter("hidebroken"))
        assertEquals("48", url.queryParameter("limit"))
    }

    @Test
    fun `top tags parse names and counts and drop blank names`() = runTest {
        val body = """[{"name":"pop","stationcount":5321},{"name":"  ","stationcount":9},""" +
            """{"name":"hip hop","stationcount":800,"other":true}]"""
        val transport = FakeTransport { Reply.Http(body = body) }

        val tags = directory(transport).topTags(10).getOrThrow()

        assertEquals(listOf(Tag("pop", 5321), Tag("hip hop", 800)), tags)
        assertEquals(listOf("Pop", "Hip Hop"), tags.map { it.displayName })
    }

    // endregion

    // region click reporting

    @Test
    fun `click is reported to the url endpoint for the station uuid`() = runTest {
        val transport = FakeTransport { Reply.Http(body = """{"ok":true}""") }

        assertTrue(directory(transport).reportClick(uuid).isSuccess)

        val request = transport.requests.single()
        assertEquals("/json/url/$uuid", request.url.encodedPath)
        assertEquals("GET", request.method)
        assertNull(request.url.queryParameter("limit"))
        assertNull(request.url.queryParameter("hidebroken"))
    }

    @Test
    fun `clicks for non radio-browser ids make no request`() = runTest {
        val transport = FakeTransport()
        val directory = directory(transport)

        listOf("imported:https://example.com/stream", "sir-default", "curated-worldwide-fm", "", "not-a-uuid")
            .forEach { assertTrue(directory.reportClick(it).isSuccess) }

        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `click reporting fails over like any other request`() = runTest {
        val transport = FakeTransport { url ->
            if (url.host == "m1.example") Reply.Http(code = 502) else Reply.Http(body = "{}")
        }

        assertTrue(directory(transport).reportClick(uuid).isSuccess)
        assertEquals(listOf("m1.example", "m2.example"), transport.urls.map { it.host })
    }

    // endregion

    // region failover

    @Test
    fun `transport failure fails over to the next mirror`() = runTest {
        val transport = FakeTransport { url ->
            if (url.host == "m1.example") Reply.Fail() else Reply.Http(body = "[" + stationJson() + "]")
        }

        val stations = directory(transport).topStations(5).getOrThrow()

        assertEquals(1, stations.size)
        assertEquals(listOf("m1.example", "m2.example"), transport.urls.map { it.host })
    }

    @Test
    fun `server errors fail over but client errors do not`() = runTest {
        val serverError = FakeTransport { url ->
            if (url.host == "m1.example") Reply.Http(code = 503) else Reply.Http()
        }
        assertTrue(directory(serverError).topStations(5).isSuccess)
        assertEquals(2, serverError.requests.size)

        val clientError = FakeTransport { Reply.Http(code = 404) }
        val result = directory(clientError).topStations(5)
        assertEquals(404, (result.exceptionOrNull() as HttpStatusException).code)
        assertEquals(1, clientError.requests.size)
    }

    @Test
    fun `decode errors are not retried`() = runTest {
        val transport = FakeTransport { Reply.Http(body = "{not json") }

        val result = directory(transport).topStations(5)

        assertTrue(result.exceptionOrNull() is SerializationException)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `a blank body is treated as a broken mirror`() = runTest {
        val transport = FakeTransport { url ->
            if (url.host == "m1.example") Reply.Http(body = " ") else Reply.Http(body = "[]")
        }

        assertTrue(directory(transport).topStations(5).isSuccess)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `attempts are bounded with exponential backoff`() = runTest {
        val transport = FakeTransport { Reply.Fail() }

        val result = directory(transport).search(StationQuery("x"))

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(
            listOf("m1.example", "m2.example", "m3.example"),
            transport.urls.map { it.host }
        )
        // 350 ms after the first failure, 700 ms after the second, none after the last.
        assertEquals(350L + 700L, testScheduler.currentTime)
    }

    @Test
    fun `attempts wrap around a short mirror list`() = runTest {
        val transport = FakeTransport { Reply.Fail() }

        directory(transport, mirrorList = listOf("https://only.example")).topStations(5)

        assertEquals(3, transport.requests.size)
    }

    @Test
    fun `the failover budget stops retries before backoff would exceed it`() = runTest {
        val transport = FakeTransport { Reply.Fail() }

        directory(transport, budgetMs = 500).topStations(5)

        // After the first failure 350 ms fits; after the second, 350 + 700 does not.
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `a slow mirror run cannot exceed the budget`() = runTest {
        var now = 0L
        val transport = FakeTransport {
            now += 40_000L * 1_000_000 // every attempt "takes" 40 s
            Reply.Fail()
        }

        directory(transport, nanoTime = { now }).topStations(5)

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `no usable mirror is a failure`() = runTest {
        val transport = FakeTransport()

        val result = directory(transport, mirrorList = listOf("not a url")).topStations(5)

        assertTrue(result.isFailure)
        assertFalse(transport.requests.isNotEmpty())
    }

    // endregion
}
