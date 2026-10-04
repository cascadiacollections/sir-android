package com.cascadiacollections.sir.wear

import androidx.media3.common.MimeTypes
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.WatchStationPayload
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

        assertThat(item.localConfiguration!!.uri.toString()).isEqualTo("https://cdn.example.com/a")
        assertThat(item.mediaId).isEqualTo("a")
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("Station a")
        assertThat(item.mediaMetadata.artist.toString()).isEqualTo("Live stream")
        assertThat(item.mediaMetadata.artworkUri.toString()).isEqualTo("https://example.com/a.png")
        assertThat(item.localConfiguration!!.mimeType).isNull()
    }

    @Test
    fun `HLS stations get the HLS MIME type`() {
        val item = WearStations.mediaItem(station("h").copy(hls = 1), "Live stream")
        assertThat(item.localConfiguration!!.mimeType).isEqualTo(MimeTypes.APPLICATION_M3U8)
    }

    @Test
    fun `station without an id is keyed by its stream URL`() {
        val item = WearStations.mediaItem(Station(name = "Custom", url = "https://custom.example/s"), "Live stream")
        assertThat(item.mediaId).isEqualTo("https://custom.example/s")
    }

    @Test
    fun `default station plays the SIR stream`() {
        val item = WearStations.mediaItem(default, "Live stream")
        assertThat(item.mediaId).isEqualTo(WearStations.DEFAULT_STATION_ID)
        assertThat(item.localConfiguration!!.uri.toString()).isEqualTo("https://sir.example/stream")
    }

    @Test
    fun `list puts the SIR stream first then last and recents without duplicates`() {
        val payload = WatchStationPayload(
            last = station("b"),
            recents = listOf(station("a"), station("b"), Station(id = "dead", name = "Dead"))
        )

        val ids = WearStations.list(default, payload).map { it.id }

        assertThat(ids).containsExactly("sir", "b", "a")
    }

    @Test
    fun `list with nothing synced is just the SIR stream`() {
        assertThat(WearStations.list(default, WatchStationPayload())).containsExactly(default)
    }

    @Test
    fun `abbreviate keeps short names and shortens long ones`() {
        assertThat(WearStations.abbreviate("SIR")).isEqualTo("SIR")
        assertThat(WearStations.abbreviate("KEXP 90.3 FM Seattle")).isEqualTo("KEXP")
        val long = WearStations.abbreviate("Radiodiffusion")
        assertThat(long.length).isEqualTo(WearStations.SHORT_TEXT_MAX)
        assertThat(long).endsWith("…")
    }

    @Test
    fun `station intent extra round trips`() {
        val station = station("a").copy(hls = 1)
        val encoded = WearPlaybackService.playStationIntent(
            org.robolectric.RuntimeEnvironment.getApplication(),
            station
        ).getStringExtra(WearPlaybackService.EXTRA_STATION)!!
        assertThat(WearPlaybackService.decodeStation(encoded)).isEqualTo(station)
        assertThat(WearPlaybackService.decodeStation("garbage")).isNull()
    }
}
