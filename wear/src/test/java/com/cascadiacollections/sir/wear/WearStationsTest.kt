package com.cascadiacollections.sir.wear

import androidx.media3.common.MimeTypes
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.WatchStationPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric only for android.net.Uri, which MediaItem.Builder parses.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearStationsTest {

    private val default = WearStations.default("SIR", "https://sir.example/stream")

    private fun station(id: String) = Station(id = id, name = "Station $id", url = "https://example.com/$id")

    @Test
    fun `media item uses the resolved stream URL and the station name`() {
        val station = station(
            "a"
        ).copy(urlResolved = "https://cdn.example.com/a", favicon = "https://example.com/a.png")

        val item = WearStations.mediaItem(station, "Live stream")

        assertEquals("https://cdn.example.com/a", item.localConfiguration!!.uri.toString())
        assertEquals("a", item.mediaId)
        assertEquals("Station a", item.mediaMetadata.title.toString())
        assertEquals("Live stream", item.mediaMetadata.artist.toString())
        assertEquals("https://example.com/a.png", item.mediaMetadata.artworkUri.toString())
        assertNull(item.localConfiguration!!.mimeType)
    }

    @Test
    fun `HLS stations get the HLS MIME type`() {
        val item = WearStations.mediaItem(station("h").copy(hls = 1), "Live stream")
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration!!.mimeType)
    }

    @Test
    fun `station without an id is keyed by its stream URL`() {
        val item = WearStations.mediaItem(Station(name = "Custom", url = "https://custom.example/s"), "Live stream")
        assertEquals("https://custom.example/s", item.mediaId)
    }

    @Test
    fun `default station plays the SIR stream`() {
        val item = WearStations.mediaItem(default, "Live stream")
        assertEquals(WearStations.DEFAULT_STATION_ID, item.mediaId)
        assertEquals("https://sir.example/stream", item.localConfiguration!!.uri.toString())
    }

    @Test
    fun `list puts the SIR stream first then last and recents without duplicates`() {
        val payload = WatchStationPayload(
            last = station("b"),
            recents = listOf(station("a"), station("b"), Station(id = "dead", name = "Dead"))
        )

        val ids = WearStations.list(default, payload).map { it.id }

        assertEquals(listOf("sir", "b", "a"), ids)
    }

    @Test
    fun `list with nothing synced is just the SIR stream`() {
        assertEquals(listOf(default), WearStations.list(default, WatchStationPayload()))
    }

    @Test
    fun `abbreviate keeps short names and shortens long ones`() {
        assertEquals("SIR", WearStations.abbreviate("SIR"))
        assertEquals("KEXP", WearStations.abbreviate("KEXP 90.3 FM Seattle"))
        val long = WearStations.abbreviate("Radiodiffusion")
        assertEquals(WearStations.SHORT_TEXT_MAX, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `station intent extra round trips`() {
        val station = station("a").copy(hls = 1)
        val encoded = WearPlaybackService.playStationIntent(
            org.robolectric.RuntimeEnvironment.getApplication(),
            station
        ).getStringExtra(WearPlaybackService.EXTRA_STATION)!!
        assertEquals(station, WearPlaybackService.decodeStation(encoded))
        assertNull(WearPlaybackService.decodeStation("garbage"))
    }
}
