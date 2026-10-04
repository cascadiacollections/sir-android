package com.cascadiacollections.sir

import com.cascadiacollections.sir.NowPlayingAnnouncement.NothingPlaying
import com.cascadiacollections.sir.NowPlayingAnnouncement.StationOnly
import com.cascadiacollections.sir.NowPlayingAnnouncement.Track
import org.junit.Assert.assertEquals
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

        assertEquals(Track("Dreams", "Fleetwood Mac", "KEXP"), announcement)
        assertEquals("Now playing Dreams by Fleetwood Mac on KEXP", announcement.text(templates))
    }

    @Test
    fun `a resolved title without an artist leaves the artist out`() {
        val announcement = announce(artist = "Live stream", hasResolvedArtist = false)

        assertEquals(Track("Dreams", null, "KEXP"), announcement)
        assertEquals("Now playing Dreams on KEXP", announcement.text(templates))
    }

    @Test
    fun `a blank resolved artist is treated as missing`() {
        assertEquals("Now playing Dreams on KEXP", announce(artist = "  ").text(templates))
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

        assertEquals(StationOnly("KEXP"), announcement)
        assertEquals("KEXP is playing", announcement.text(templates))
    }

    @Test
    fun `a blank resolved title announces the station`() {
        assertEquals(StationOnly("KEXP"), announce(title = " "))
    }

    @Test
    fun `a missing station name falls back to the app's stream`() {
        assertEquals("Now playing Dreams by Fleetwood Mac on SIR", announce(station = null).text(templates))
        assertEquals("SIR is playing", announce(station = "", hasResolvedTrack = false).text(templates))
    }

    @Test
    fun `nothing is playing when playback is not active`() {
        val announcement = announce(isPlaying = false)

        assertEquals(NothingPlaying, announcement)
        assertEquals("Nothing is playing", announcement.text(templates))
    }

    @Test
    fun `values are trimmed`() {
        assertEquals(
            Track("Dreams", "Fleetwood Mac", "KEXP"),
            announce(title = " Dreams ", artist = "Fleetwood Mac ", station = " KEXP")
        )
    }
}
