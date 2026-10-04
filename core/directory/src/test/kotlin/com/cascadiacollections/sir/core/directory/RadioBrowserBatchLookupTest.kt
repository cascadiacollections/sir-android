package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** `getStations(ids)`: the batched `byuuid` lookup behind the saved-station refresh. */
class RadioBrowserBatchLookupTest {

    private fun uuid(n: Int) = "%08x-0000-4000-8000-000000000000".format(n)

    private fun stationJson(id: String) =
        """{"stationuuid":"$id","name":"S $id","url":"https://s.example/$id","url_resolved":"https://r.example/$id"}"""

    private fun TestScope.directory(transport: FakeTransport) = RadioBrowserDirectory(
        httpClient = transport.client,
        mirrorProvider = RotatingMirrorProvider(listOf("https://m1.example"), shuffle = { it }),
        ioDispatcher = StandardTestDispatcher(testScheduler),
        nanoTime = { testScheduler.currentTime * 1_000_000 },
        retryPolicy = RetryPolicy(maxAttempts = 1)
    )

    /** Answers each `byuuid` request with the stations it asked for. */
    private fun echo() = FakeTransport { url ->
        val ids = url.queryParameter("uuids").orEmpty().split(',').filter { it.isNotEmpty() }
        Reply.Http(body = ids.joinToString(",", "[", "]") { stationJson(it) })
    }

    @Test
    fun `looks stations up in one byuuid request`() = runTest {
        val transport = echo()
        val ids = listOf(uuid(1), uuid(2), uuid(3))

        val stations = directory(transport).getStations(ids).getOrThrow()

        val url = transport.urls.single()
        assertThat(url.encodedPath).isEqualTo("/json/stations/byuuid")
        assertThat(url.queryParameter("uuids")).isEqualTo(ids.joinToString(","))
        assertThat(stations.map { it.id }).isEqualTo(ids)
        assertThat(stations.first().urlResolved).isEqualTo("https://r.example/${uuid(1)}")
    }

    @Test
    fun `batches at most 100 ids per request`() = runTest {
        val transport = echo()
        val ids = (1..250).map(::uuid)

        val stations = directory(transport).getStations(ids).getOrThrow()

        assertThat(transport.urls.map { it.queryParameter("uuids")!!.split(',').size })
            .containsExactly(100, 100, 50)
        assertThat(stations.map { it.id }).isEqualTo(ids)
    }

    @Test
    fun `skips non radio-browser ids and duplicates without a request`() = runTest {
        val transport = echo()

        assertThat(directory(transport).getStations(listOf("sir-default", "imported:x")).getOrThrow()).isEmpty()
        assertThat(transport.urls).isEmpty()

        directory(transport).getStations(listOf(uuid(1), "curated-1", uuid(1))).getOrThrow()
        assertThat(transport.urls.single().queryParameter("uuids")).isEqualTo(uuid(1))
    }

    @Test
    fun `any failed batch fails the whole lookup`() = runTest {
        var calls = 0
        val transport = FakeTransport {
            calls++
            if (calls == 2) Reply.Fail(IOException("offline")) else Reply.Http(body = "[]")
        }

        assertThat(directory(transport).getStations((1..150).map(::uuid))).isFailure()
    }

    @Test
    fun `the decorators pass lookups through uncached and without fallback`() = runTest {
        val transport = FakeTransport { Reply.Fail(IOException("offline")) }
        val chain = CuratedFallbackDirectory(CachingRadioDirectory(directory(transport)))

        assertThat(chain.getStations(listOf(uuid(1)))).isFailure()
        assertThat(chain.getStations(listOf(uuid(1)))).isFailure()
        assertThat(transport.urls).hasSize(2)
    }
}
