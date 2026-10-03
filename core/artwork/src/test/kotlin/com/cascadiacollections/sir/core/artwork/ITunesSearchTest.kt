package com.cascadiacollections.sir.core.artwork

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ITunesSearchTest {

    @Test
    fun `search url carries the ShoutKit query`() {
        val url = ITunesSearch.searchUrl("Daft Punk", "One More Time", "gb")
        assertEquals("https", url.scheme)
        assertEquals("itunes.apple.com", url.host)
        assertEquals("/search", url.encodedPath)
        assertEquals("Daft Punk One More Time", url.queryParameter("term"))
        assertEquals("music", url.queryParameter("media"))
        assertEquals("song", url.queryParameter("entity"))
        assertEquals("1", url.queryParameter("limit"))
        assertEquals("GB", url.queryParameter("country"))
    }

    @Test
    fun `term is encoded rather than hand escaped`() {
        val url = ITunesSearch.searchUrl("AC/DC", "Rock & Roll #1?", "US")
        assertEquals("AC/DC Rock & Roll #1?", url.queryParameter("term"))
        assertEquals(1, url.queryParameterValues("term").size)
    }

    @Test
    fun `storefront falls back to US for anything that is not a two letter code`() {
        assertEquals("US", ITunesSearch.storefront(null))
        assertEquals("US", ITunesSearch.storefront(""))
        assertEquals("US", ITunesSearch.storefront("419"))
        assertEquals("US", ITunesSearch.storefront("USA"))
        assertEquals("CN", ITunesSearch.storefront("cn"))
    }

    @Test
    fun `parse upscales the thumbnail and keeps the track page`() {
        val body = """
            {"resultCount":1,"results":[{"wrapperType":"track","trackName":"One More Time",
            "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/cd/100x100bb.jpg",
            "trackViewUrl":"https://music.apple.com/us/album/one-more-time/697194953?i=697195462"}]}
        """.trimIndent()
        val art = ITunesSearch.parse(body)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/cd/600x600bb.jpg", art?.artworkUrl)
        assertEquals("https://music.apple.com/us/album/one-more-time/697194953?i=697195462", art?.trackViewUrl)
    }

    @Test
    fun `parse returns null for no results or no artwork`() {
        assertNull(ITunesSearch.parse("""{"resultCount":0,"results":[]}"""))
        assertNull(ITunesSearch.parse("""{"results":[{"trackViewUrl":"https://x"}]}"""))
        assertNull(ITunesSearch.parse("""{"results":[{"artworkUrl100":""}]}"""))
    }

    @Test
    fun `parse tolerates a missing track page`() {
        val art = ITunesSearch.parse("""{"results":[{"artworkUrl100":"https://a/100x100bb.jpg"}]}""")
        assertEquals("https://a/600x600bb.jpg", art?.artworkUrl)
        assertNull(art?.trackViewUrl)
    }

    @Test(expected = SerializationException::class)
    fun `parse rejects a body that is not a search response`() {
        ITunesSearch.parse("<html>rate limited</html>")
    }
}
