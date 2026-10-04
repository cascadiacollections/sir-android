package com.cascadiacollections.sir.core.artwork

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
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

        assertThat(first).isEqualTo(AlbumArt("https://a/600x600bb.jpg", "https://music.apple.com/t"))
        assertThat(second).isEqualTo(first)
        assertThat(transport.requests).hasSize(1)
    }

    @Test
    fun `a miss is cached too`() = runTest {
        val transport = FakeTransport(Reply.Http())
        val lookup = AlbumArtLookup(transport.client)

        assertThat(lookup.lookup("Artist", "Unknown", "US")).isNull()
        assertThat(lookup.isCached("Artist", "Unknown")).isTrue()
        assertThat(lookup.lookup("Artist", "Unknown", "US")).isNull()
        assertThat(transport.requests).hasSize(1)
    }

    @Test
    fun `failures are not cached`() = runTest {
        val transport = FakeTransport(Reply.Fail())
        val lookup = AlbumArtLookup(transport.client)

        assertThat(lookup.lookup("A", "T", "US")).isNull()
        assertThat(lookup.isCached("A", "T")).isFalse()

        transport.reply = Reply.Http(code = 503, body = "busy")
        assertThat(lookup.lookup("A", "T", "US")).isNull()
        assertThat(lookup.isCached("A", "T")).isFalse()

        transport.reply = Reply.Http(body = "<html/>")
        assertThat(lookup.lookup("A", "T", "US")).isNull()
        assertThat(lookup.isCached("A", "T")).isFalse()

        transport.reply = hit
        assertThat(lookup.lookup("A", "T", "US")?.artworkUrl).isEqualTo("https://a/600x600bb.jpg")
        assertThat(transport.requests).hasSize(4)
    }

    @Test
    fun `blank artist or title never reaches the network`() = runTest {
        val transport = FakeTransport(hit)
        val lookup = AlbumArtLookup(transport.client)

        assertThat(lookup.lookup(null, "Title", "US")).isNull()
        assertThat(lookup.lookup("Artist", " ", "US")).isNull()
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `requests identify the app`() = runTest {
        val transport = FakeTransport(hit)
        AlbumArtLookup(transport.client, userAgent = "SIR-Test/1").lookup("A", "T", "de")

        val request = transport.requests.single()
        assertThat(request.header("User-Agent")).isEqualTo("SIR-Test/1")
        assertThat(request.url.queryParameter("country")).isEqualTo("DE")
    }

    @Test
    fun `cache is bounded`() = runTest {
        val transport = FakeTransport(hit)
        val lookup = AlbumArtLookup(transport.client, cacheSize = 2)

        lookup.lookup("A", "1", "US")
        lookup.lookup("A", "2", "US")
        lookup.lookup("A", "3", "US")

        assertThat(lookup.isCached("A", "1")).isFalse()
        assertThat(lookup.isCached("A", "3")).isTrue()
    }

    @Test
    fun `default client is bounded to eight seconds`() {
        val client = AlbumArtLookup.defaultHttpClient()
        assertThat(client.callTimeoutMillis).isEqualTo(8_000)
        assertThat(client.connectTimeoutMillis).isEqualTo(8_000)
    }
}
