package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SavedStationRefreshTest {

    private val saved = Station(
        id = "a",
        name = "My Jazz",
        url = "https://a.example/listen.pls",
        urlResolved = "https://old.example/stream",
        favicon = "https://a.example/icon.png",
        bitrate = 64,
        codec = "MP3",
        countryCode = "GB",
        tags = "jazz"
    )

    private val fetched = Station(
        id = "a",
        name = "Jazz FM [HD]",
        url = "https://a.example/listen.pls",
        urlResolved = "https://new.example/stream.m3u8",
        favicon = "https://cdn.example/new.png",
        bitrate = 128,
        codec = "AAC",
        hls = 1,
        countryCode = "US",
        tags = "jazz,smooth"
    )

    @Test
    fun `refreshes the stream description when the url is unchanged`() {
        val result = SavedStationRefresh.merge(listOf(saved), listOf(fetched))

        assertEquals(1, result.updated)
        assertEquals(
            saved.copy(urlResolved = "https://new.example/stream.m3u8", hls = 1, bitrate = 128, codec = "AAC"),
            result.stations.single()
        )
    }

    @Test
    fun `never overwrites the name, url, artwork or other fields`() {
        val station = SavedStationRefresh.merge(listOf(saved), listOf(fetched)).stations.single()

        assertEquals("My Jazz", station.name)
        assertEquals("https://a.example/listen.pls", station.url)
        assertEquals("https://a.example/icon.png", station.favicon)
        assertEquals("GB", station.countryCode)
        assertEquals("jazz", station.tags)
    }

    @Test
    fun `an edited url keeps the station exactly as the user left it`() {
        val edited = saved.withUrl("https://mine.example/stream")

        val result = SavedStationRefresh.merge(listOf(edited), listOf(fetched))

        assertEquals(0, result.updated)
        assertEquals(edited, result.stations.single())
        assertEquals("https://mine.example/stream", result.stations.single().streamUrl)
    }

    @Test
    fun `missing artwork is filled in`() {
        val noArt = saved.copy(favicon = null)
        val blankArt = saved.copy(id = "b", favicon = "")

        val result = SavedStationRefresh.merge(listOf(noArt, blankArt), listOf(fetched, fetched.copy(id = "b")))

        assertEquals(
            listOf("https://cdn.example/new.png", "https://cdn.example/new.png"),
            result.stations.map { it.favicon }
        )
    }

    @Test
    fun `a blank directory favicon never clears saved artwork`() {
        val result = SavedStationRefresh.merge(listOf(saved.copy(favicon = null)), listOf(fetched.copy(favicon = "")))

        assertEquals(null, result.stations.single().favicon)
    }

    @Test
    fun `order is kept and stations the directory did not return are untouched`() {
        val other = Station(id = "imported:x", name = "Mine", url = "https://x.example")
        val third = saved.copy(id = "c", name = "Third")

        val result = SavedStationRefresh.merge(listOf(other, saved, third), listOf(fetched))

        assertEquals(listOf("imported:x", "a", "c"), result.stations.map { it.id })
        assertSame(other, result.stations[0])
        assertSame(third, result.stations[2])
        assertEquals(1, result.updated)
    }

    @Test
    fun `nothing changed returns the saved list itself`() {
        val current = saved.copy(urlResolved = fetched.urlResolved, hls = 1, bitrate = 128, codec = "AAC")
        val list = listOf(current)

        val result = SavedStationRefresh.merge(list, listOf(fetched))

        assertEquals(0, result.updated)
        assertSame(list, result.stations)
    }

    @Test
    fun `an unplayable directory entry does not blank the stream`() {
        val result = SavedStationRefresh.merge(listOf(saved), listOf(fetched.copy(urlResolved = "", url = "")))

        assertEquals(saved, result.stations.single())
    }

    @Test
    fun `empty inputs are a no-op`() {
        assertEquals(0, SavedStationRefresh.merge(emptyList(), listOf(fetched)).updated)
        assertEquals(listOf(saved), SavedStationRefresh.merge(listOf(saved), emptyList()).stations)
    }
}
