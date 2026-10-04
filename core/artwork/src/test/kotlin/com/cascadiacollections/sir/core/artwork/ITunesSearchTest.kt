package com.cascadiacollections.sir.core.artwork

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.serialization.SerializationException
import org.junit.Test

class ITunesSearchTest {

    @Test
    fun `search url carries the ShoutKit query`() {
        val url = ITunesSearch.searchUrl("Daft Punk", "One More Time", "gb")
        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("itunes.apple.com")
        assertThat(url.encodedPath).isEqualTo("/search")
        assertThat(url.queryParameter("term")).isEqualTo("Daft Punk One More Time")
        assertThat(url.queryParameter("media")).isEqualTo("music")
        assertThat(url.queryParameter("entity")).isEqualTo("song")
        assertThat(url.queryParameter("limit")).isEqualTo("1")
        assertThat(url.queryParameter("country")).isEqualTo("GB")
    }

    @Test
    fun `term is encoded rather than hand escaped`() {
        val url = ITunesSearch.searchUrl("AC/DC", "Rock & Roll #1?", "US")
        assertThat(url.queryParameter("term")).isEqualTo("AC/DC Rock & Roll #1?")
        assertThat(url.queryParameterValues("term")).hasSize(1)
    }

    @Test
    fun `storefront falls back to US for anything that is not a two letter code`() {
        assertThat(ITunesSearch.storefront(null)).isEqualTo("US")
        assertThat(ITunesSearch.storefront("")).isEqualTo("US")
        assertThat(ITunesSearch.storefront("419")).isEqualTo("US")
        assertThat(ITunesSearch.storefront("USA")).isEqualTo("US")
        assertThat(ITunesSearch.storefront("cn")).isEqualTo("CN")
    }

    @Test
    fun `parse upscales the thumbnail and keeps the track page`() {
        val body = """
            {"resultCount":1,"results":[{"wrapperType":"track","trackName":"One More Time",
            "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/cd/100x100bb.jpg",
            "trackViewUrl":"https://music.apple.com/us/album/one-more-time/697194953?i=697195462"}]}
        """.trimIndent()
        val art = ITunesSearch.parse(body)
        assertThat(art?.artworkUrl).isEqualTo("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/cd/600x600bb.jpg")
        assertThat(art?.trackViewUrl).isEqualTo("https://music.apple.com/us/album/one-more-time/697194953?i=697195462")
    }

    @Test
    fun `parse returns null for no results or no artwork`() {
        assertThat(ITunesSearch.parse("""{"resultCount":0,"results":[]}""")).isNull()
        assertThat(ITunesSearch.parse("""{"results":[{"trackViewUrl":"https://x"}]}""")).isNull()
        assertThat(ITunesSearch.parse("""{"results":[{"artworkUrl100":""}]}""")).isNull()
    }

    @Test
    fun `parse tolerates a missing track page`() {
        val art = ITunesSearch.parse("""{"results":[{"artworkUrl100":"https://a/100x100bb.jpg"}]}""")
        assertThat(art?.artworkUrl).isEqualTo("https://a/600x600bb.jpg")
        assertThat(art?.trackViewUrl).isNull()
    }

    @Test(expected = SerializationException::class)
    fun `parse rejects a body that is not a search response`() {
        ITunesSearch.parse("<html>rate limited</html>")
    }
}
