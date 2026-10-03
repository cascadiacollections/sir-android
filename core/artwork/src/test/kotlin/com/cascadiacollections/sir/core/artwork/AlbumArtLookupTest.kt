package com.cascadiacollections.sir.core.artwork

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtLookupTest {

    private val hit = Reply.Http(
        body = """{"results":[{"artworkUrl100":"https://a/100x100bb.jpg","trackViewUrl":"https://music.apple.com/t"}]}"""
    )

    @Test
    fun `a hit is fetched once and then served from cache`() = runTest {
        val transport = FakeTransport(hit)
        val lookup = AlbumArtLookup(transport.client)

        val first = lookup.lookup("Artist", "Title", "US")
        val second = lookup.lookup("  artist ", "TITLE", "US")

        assertEquals(AlbumArt("https://a/600x600bb.jpg", "https://music.apple.com/t"), first)
        assertEquals(first, second)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `a miss is cached too`() = runTest {
        val transport = FakeTransport(Reply.Http())
        val lookup = AlbumArtLookup(transport.client)

        assertNull(lookup.lookup("Artist", "Unknown", "US"))
        assertTrue(lookup.isCached("Artist", "Unknown"))
        assertNull(lookup.lookup("Artist", "Unknown", "US"))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `failures are not cached`() = runTest {
        val transport = FakeTransport(Reply.Fail())
        val lookup = AlbumArtLookup(transport.client)

        assertNull(lookup.lookup("A", "T", "US"))
        assertFalse(lookup.isCached("A", "T"))

        transport.reply = Reply.Http(code = 503, body = "busy")
        assertNull(lookup.lookup("A", "T", "US"))
        assertFalse(lookup.isCached("A", "T"))

        transport.reply = Reply.Http(body = "<html/>")
        assertNull(lookup.lookup("A", "T", "US"))
        assertFalse(lookup.isCached("A", "T"))

        transport.reply = hit
        assertEquals("https://a/600x600bb.jpg", lookup.lookup("A", "T", "US")?.artworkUrl)
        assertEquals(4, transport.requests.size)
    }

    @Test
    fun `blank artist or title never reaches the network`() = runTest {
        val transport = FakeTransport(hit)
        val lookup = AlbumArtLookup(transport.client)

        assertNull(lookup.lookup(null, "Title", "US"))
        assertNull(lookup.lookup("Artist", " ", "US"))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `requests identify the app`() = runTest {
        val transport = FakeTransport(hit)
        AlbumArtLookup(transport.client, userAgent = "SIR-Test/1").lookup("A", "T", "de")

        val request = transport.requests.single()
        assertEquals("SIR-Test/1", request.header("User-Agent"))
        assertEquals("DE", request.url.queryParameter("country"))
    }

    @Test
    fun `cache is bounded`() = runTest {
        val transport = FakeTransport(hit)
        val lookup = AlbumArtLookup(transport.client, cacheSize = 2)

        lookup.lookup("A", "1", "US")
        lookup.lookup("A", "2", "US")
        lookup.lookup("A", "3", "US")

        assertFalse(lookup.isCached("A", "1"))
        assertTrue(lookup.isCached("A", "3"))
    }

    @Test
    fun `default client is bounded to eight seconds`() {
        val client = AlbumArtLookup.defaultHttpClient()
        assertEquals(8_000, client.callTimeoutMillis)
        assertEquals(8_000, client.connectTimeoutMillis)
    }
}
