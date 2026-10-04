package com.cascadiacollections.sir.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        assertEquals(payload, decoded)
        assertTrue(decoded.last!!.isHls)
    }

    @Test
    fun `round trips an empty payload`() {
        val payload = WatchStationPayload()
        assertEquals(payload, WatchStationSync.decode(WatchStationSync.encode(payload)))
    }

    @Test
    fun `missing fields default`() {
        assertEquals(WatchStationPayload(), WatchStationSync.decode("{}"))
        val onlyRecents = WatchStationSync.decode("""{"recents":[{"stationuuid":"x","name":"X","url":"https://x"}]}""")
        assertNull(onlyRecents.last)
        assertEquals("x", onlyRecents.recents.single().id)
    }

    @Test
    fun `old station fields decode and unknown fields are ignored`() {
        // A station persisted before url_resolved/hls existed, plus a field from a newer build.
        val raw = """{"last":{"stationuuid":"a","name":"A","url":"https://a"},"recents":[],"schema":2}"""
        val decoded = WatchStationSync.decode(raw)
        assertEquals("https://a", decoded.last!!.streamUrl)
        assertEquals(false, decoded.last!!.isHls)
    }

    @Test
    fun `unreadable input decodes to empty payload`() {
        assertEquals(WatchStationPayload(), WatchStationSync.decode(null))
        assertEquals(WatchStationPayload(), WatchStationSync.decode(""))
        assertEquals(WatchStationPayload(), WatchStationSync.decode("{\"last\":"))
        assertEquals(WatchStationPayload(), WatchStationSync.decode("[]"))
    }

    @Test
    fun `payload caps recents and drops unplayable and duplicate stations`() {
        val recents = (1..15).map { station("s$it") } + station("s1") + station("dead", url = "")
        val payload = WatchStationSync.payload(last = station("dead", url = ""), recents = recents)

        assertNull(payload.last)
        assertEquals(WatchStationSync.MAX_RECENTS, payload.recents.size)
        assertEquals((1..10).map { "s$it" }, payload.recents.map { it.id })
    }
}
