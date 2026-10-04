package com.cascadiacollections.sir.core.model

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class WatchStationSyncTest {

    private fun station(id: String, url: String = "https://example.com/$id") =
        Station(id = id, name = "Station $id", url = url)

    @Test
    fun `payload round trips`() {
        val payload = WatchStationPayload(
            last = station("a").copy(urlResolved = "https://cdn.example.com/a.m3u8", hls = 1),
            recents = listOf(station("a"), station("b"))
        )

        val decoded = WatchStationSync.decode(WatchStationSync.encode(payload))

        assertThat(decoded).isEqualTo(payload)
        assertThat(decoded.last!!.isHls).isTrue()
    }

    @Test
    fun `round trips an empty payload`() {
        val payload = WatchStationPayload()
        assertThat(WatchStationSync.decode(WatchStationSync.encode(payload))).isEqualTo(payload)
    }

    @Test
    fun `missing fields default`() {
        assertThat(WatchStationSync.decode("{}")).isEqualTo(WatchStationPayload())
        val onlyRecents = WatchStationSync.decode("""{"recents":[{"stationuuid":"x","name":"X","url":"https://x"}]}""")
        assertThat(onlyRecents.last).isNull()
        assertThat(onlyRecents.recents.single().id).isEqualTo("x")
    }

    @Test
    fun `old station fields decode and unknown fields are ignored`() {
        // A station persisted before url_resolved/hls existed, plus a field from a newer build.
        val raw = """{"last":{"stationuuid":"a","name":"A","url":"https://a"},"recents":[],"schema":2}"""
        val decoded = WatchStationSync.decode(raw)
        assertThat(decoded.last!!.streamUrl).isEqualTo("https://a")
        assertThat(decoded.last!!.isHls).isFalse()
    }

    @Test
    fun `unreadable input decodes to empty payload`() {
        assertThat(WatchStationSync.decode(null)).isEqualTo(WatchStationPayload())
        assertThat(WatchStationSync.decode("")).isEqualTo(WatchStationPayload())
        assertThat(WatchStationSync.decode("{\"last\":")).isEqualTo(WatchStationPayload())
        assertThat(WatchStationSync.decode("[]")).isEqualTo(WatchStationPayload())
    }

    @Test
    fun `payload caps recents and drops unplayable and duplicate stations`() {
        val recents = (1..15).map { station("s$it") } + station("s1") + station("dead", url = "")
        val payload = WatchStationSync.payload(last = station("dead", url = ""), recents = recents)

        assertThat(payload.last).isNull()
        assertThat(payload.recents).hasSize(WatchStationSync.MAX_RECENTS)
        assertThat(payload.recents.map { it.id }).isEqualTo((1..10).map { "s$it" })
    }
}
