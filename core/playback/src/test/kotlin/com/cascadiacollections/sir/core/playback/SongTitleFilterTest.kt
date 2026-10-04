package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class SongTitleFilterTest {

    private fun accepts(title: String?, artist: String? = null, station: String? = "KEXP 90.3 FM") =
        SongTitleFilter.isLikelySongTitle(IcyTrack(title = title, artist = artist), station)

    @Test
    fun `an ordinary song passes`() {
        assertThat(accepts("Dreams", artist = "Fleetwood Mac")).isTrue()
    }

    @Test
    fun `a one-word title with no artist still passes`() {
        assertThat(accepts("September")).isTrue()
    }

    @Test
    fun `an unusual title is kept rather than guessed about`() {
        // The filter defaults to keeping everything; only positive junk signals reject.
        assertThat(accepts("¿Dónde Estás?", artist = "Café Tacvba")).isTrue()
    }

    @Test
    fun `a missing title is rejected`() {
        assertThat(accepts(null)).isFalse()
        assertThat(accepts("   ")).isFalse()
    }

    @Test
    fun `a URL is rejected`() {
        assertThat(accepts("https://kexp.org")).isFalse()
        assertThat(accepts("Listen at www.kexp.org")).isFalse()
    }

    @Test
    fun `a bare domain is rejected but a title containing a word with a dot is not`() {
        assertThat(accepts("kexp.org")).isFalse()
        assertThat(accepts("Mrs. Robinson", artist = "Simon & Garfunkel")).isTrue()
    }

    @Test
    fun `the station plugging itself is rejected regardless of punctuation`() {
        assertThat(accepts("KEXP903FM")).isFalse()
        assertThat(accepts("kexp 90.3 fm")).isFalse()
    }

    @Test
    fun `promotional copy is rejected in either half`() {
        assertThat(accepts("Download our app today")).isFalse()
        assertThat(accepts("Dreams", artist = "Brought to you by Acme")).isFalse()
    }

    @Test
    fun `a bare placeholder token is rejected`() {
        assertThat(accepts("Unknown")).isFalse()
        assertThat(accepts("offline")).isFalse()
        assertThat(accepts("SIR12345")).isFalse()
    }

    @Test
    fun `a numeric token with an artist is kept`() {
        // "1901" by Phoenix would be rejected as a bare ID with no artist, but the artist
        // makes it plainly a real track.
        assertThat(accepts("1901", artist = "Phoenix")).isTrue()
    }

    @Test
    fun `a null station name skips the station check rather than failing it`() {
        // Rejected only because it matches the station, so a null station must keep it.
        assertThat(accepts("KEXP Radio", station = "KEXP Radio")).isFalse()
        assertThat(accepts("KEXP Radio", station = null)).isTrue()
    }
}
