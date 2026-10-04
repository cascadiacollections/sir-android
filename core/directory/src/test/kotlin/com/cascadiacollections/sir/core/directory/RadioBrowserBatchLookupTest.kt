package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals("/json/stations/byuuid", url.encodedPath)
        assertEquals(ids.joinToString(","), url.queryParameter("uuids"))
        assertEquals(ids, stations.map { it.id })
        assertEquals("https://r.example/${uuid(1)}", stations.first().urlResolved)
    }

    @Test
    fun `batches at most 100 ids per request`() = runTest {
        val transport = echo()
        val ids = (1..250).map(::uuid)

        val stations = directory(transport).getStations(ids).getOrThrow()

        assertEquals(
            listOf(100, 100, 50),
            transport.urls.map {
                it.queryParameter("uuids")!!.split(',').size
            }
        )
        assertEquals(ids, stations.map { it.id })
    }

    @Test
    fun `skips non radio-browser ids and duplicates without a request`() = runTest {
        val transport = echo()

        assertEquals(
            emptyList<Station>(),
            directory(transport).getStations(listOf("sir-default", "imported:x")).getOrThrow()
        )
        assertTrue(transport.urls.isEmpty())

        directory(transport).getStations(listOf(uuid(1), "curated-1", uuid(1))).getOrThrow()
        assertEquals(uuid(1), transport.urls.single().queryParameter("uuids"))
    }

    @Test
    fun `any failed batch fails the whole lookup`() = runTest {
        var calls = 0
        val transport = FakeTransport {
            calls++
            if (calls == 2) Reply.Fail(IOException("offline")) else Reply.Http(body = "[]")
        }

        assertTrue(directory(transport).getStations((1..150).map(::uuid)).isFailure)
    }

    @Test
    fun `the decorators pass lookups through uncached and without fallback`() = runTest {
        val transport = FakeTransport { Reply.Fail(IOException("offline")) }
        val chain = CuratedFallbackDirectory(CachingRadioDirectory(directory(transport)))

        assertTrue(chain.getStations(listOf(uuid(1))).isFailure)
        assertTrue(chain.getStations(listOf(uuid(1))).isFailure)
        assertEquals(2, transport.urls.size)
    }
}
