package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTitleFilterTest {

    private fun accepts(title: String?, artist: String? = null, station: String? = "KEXP 90.3 FM") =
        SongTitleFilter.isLikelySongTitle(IcyTrack(title = title, artist = artist), station)

    @Test
    fun `an ordinary song passes`() {
        assertTrue(accepts("Dreams", artist = "Fleetwood Mac"))
    }

    @Test
    fun `a one-word title with no artist still passes`() {
        assertTrue(accepts("September"))
    }

    @Test
    fun `an unusual title is kept rather than guessed about`() {
        // The filter defaults to keeping everything; only positive junk signals reject.
        assertTrue(accepts("¿Dónde Estás?", artist = "Café Tacvba"))
    }

    @Test
    fun `a missing title is rejected`() {
        assertFalse(accepts(null))
        assertFalse(accepts("   "))
    }

    @Test
    fun `a URL is rejected`() {
        assertFalse(accepts("https://kexp.org"))
        assertFalse(accepts("Listen at www.kexp.org"))
    }

    @Test
    fun `a bare domain is rejected but a title containing a word with a dot is not`() {
        assertFalse(accepts("kexp.org"))
        assertTrue(accepts("Mrs. Robinson", artist = "Simon & Garfunkel"))
    }

    @Test
    fun `the station plugging itself is rejected regardless of punctuation`() {
        assertFalse(accepts("KEXP903FM"))
        assertFalse(accepts("kexp 90.3 fm"))
    }

    @Test
    fun `promotional copy is rejected in either half`() {
        assertFalse(accepts("Download our app today"))
        assertFalse(accepts("Dreams", artist = "Brought to you by Acme"))
    }

    @Test
    fun `a bare placeholder token is rejected`() {
        assertFalse(accepts("Unknown"))
        assertFalse(accepts("offline"))
        assertFalse(accepts("SIR12345"))
    }

    @Test
    fun `a numeric token with an artist is kept`() {
        // "1901" by Phoenix would be rejected as a bare ID with no artist, but the artist
        // makes it plainly a real track.
        assertTrue(accepts("1901", artist = "Phoenix"))
    }

    @Test
    fun `a null station name skips the station check rather than failing it`() {
        // Rejected only because it matches the station, so a null station must keep it.
        assertFalse(accepts("KEXP Radio", station = "KEXP Radio"))
        assertTrue(accepts("KEXP Radio", station = null))
    }
}
