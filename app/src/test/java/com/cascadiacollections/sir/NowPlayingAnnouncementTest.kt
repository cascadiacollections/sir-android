package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.cascadiacollections.sir.NowPlayingAnnouncement.NothingPlaying
import com.cascadiacollections.sir.NowPlayingAnnouncement.StationOnly
import com.cascadiacollections.sir.NowPlayingAnnouncement.Track
import org.junit.Test

class NowPlayingAnnouncementTest {

    private val templates = NowPlayingAnnouncement.Templates(
        trackByArtistOnStation = "Now playing %1\$s by %2\$s on %3\$s",
        trackOnStation = "Now playing %1\$s on %2\$s",
        stationPlaying = "%1\$s is playing",
        nothingPlaying = "Nothing is playing"
    )

    private fun announce(
        isPlaying: Boolean = true,
        title: String? = "Dreams",
        artist: String? = "Fleetwood Mac",
        station: String? = "KEXP",
        hasResolvedTrack: Boolean = true,
        hasResolvedArtist: Boolean = true
    ) = NowPlayingAnnouncement.from(
        isPlaying = isPlaying,
        title = title,
        artist = artist,
        station = station,
        hasResolvedTrack = hasResolvedTrack,
        hasResolvedArtist = hasResolvedArtist,
        fallbackStation = "SIR"
    )

    @Test
    fun `a resolved track with an artist names all three`() {
        val announcement = announce()

        assertThat(announcement).isEqualTo(Track("Dreams", "Fleetwood Mac", "KEXP"))
        assertThat(announcement.text(templates)).isEqualTo("Now playing Dreams by Fleetwood Mac on KEXP")
    }

    @Test
    fun `a resolved title without an artist leaves the artist out`() {
        val announcement = announce(artist = "Live stream", hasResolvedArtist = false)

        assertThat(announcement).isEqualTo(Track("Dreams", null, "KEXP"))
        assertThat(announcement.text(templates)).isEqualTo("Now playing Dreams on KEXP")
    }

    @Test
    fun `a blank resolved artist is treated as missing`() {
        assertThat(announce(artist = "  ").text(templates)).isEqualTo("Now playing Dreams on KEXP")
    }

    @Test
    fun `no resolved track announces the station`() {
        // Unresolved, the session title is the station name and the artist a generic description.
        val announcement = announce(
            title = "KEXP",
            artist = "Live stream",
            hasResolvedTrack = false,
            hasResolvedArtist = false
        )

        assertThat(announcement).isEqualTo(StationOnly("KEXP"))
        assertThat(announcement.text(templates)).isEqualTo("KEXP is playing")
    }

    @Test
    fun `a blank resolved title announces the station`() {
        assertThat(announce(title = " ")).isEqualTo(StationOnly("KEXP"))
    }

    @Test
    fun `a missing station name falls back to the app's stream`() {
        assertThat(announce(station = null).text(templates)).isEqualTo("Now playing Dreams by Fleetwood Mac on SIR")
        assertThat(announce(station = "", hasResolvedTrack = false).text(templates)).isEqualTo("SIR is playing")
    }

    @Test
    fun `nothing is playing when playback is not active`() {
        val announcement = announce(isPlaying = false)

        assertThat(announcement).isEqualTo(NothingPlaying)
        assertThat(announcement.text(templates)).isEqualTo("Nothing is playing")
    }

    @Test
    fun `values are trimmed`() {
        assertThat(announce(title = " Dreams ", artist = "Fleetwood Mac ", station = " KEXP"))
            .isEqualTo(Track("Dreams", "Fleetwood Mac", "KEXP"))
    }
}
