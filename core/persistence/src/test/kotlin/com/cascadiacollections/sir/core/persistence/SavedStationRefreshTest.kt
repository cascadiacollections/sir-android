package com.cascadiacollections.sir.core.persistence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import com.cascadiacollections.sir.core.model.Station
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

        assertThat(result.updated).isEqualTo(1)
        assertThat(result.stations.single()).isEqualTo(
            saved.copy(urlResolved = "https://new.example/stream.m3u8", hls = 1, bitrate = 128, codec = "AAC")
        )
    }

    @Test
    fun `never overwrites the name, url, artwork or other fields`() {
        val station = SavedStationRefresh.merge(listOf(saved), listOf(fetched)).stations.single()

        assertThat(station.name).isEqualTo("My Jazz")
        assertThat(station.url).isEqualTo("https://a.example/listen.pls")
        assertThat(station.favicon).isEqualTo("https://a.example/icon.png")
        assertThat(station.countryCode).isEqualTo("GB")
        assertThat(station.tags).isEqualTo("jazz")
    }

    @Test
    fun `an edited url keeps the station exactly as the user left it`() {
        val edited = saved.withUrl("https://mine.example/stream")

        val result = SavedStationRefresh.merge(listOf(edited), listOf(fetched))

        assertThat(result.updated).isEqualTo(0)
        assertThat(result.stations.single()).isEqualTo(edited)
        assertThat(result.stations.single().streamUrl).isEqualTo("https://mine.example/stream")
    }

    @Test
    fun `missing artwork is filled in`() {
        val noArt = saved.copy(favicon = null)
        val blankArt = saved.copy(id = "b", favicon = "")

        val result = SavedStationRefresh.merge(listOf(noArt, blankArt), listOf(fetched, fetched.copy(id = "b")))

        assertThat(result.stations.map { it.favicon })
            .containsExactly("https://cdn.example/new.png", "https://cdn.example/new.png")
    }

    @Test
    fun `a blank directory favicon never clears saved artwork`() {
        val result = SavedStationRefresh.merge(listOf(saved.copy(favicon = null)), listOf(fetched.copy(favicon = "")))

        assertThat(result.stations.single().favicon).isNull()
    }

    @Test
    fun `order is kept and stations the directory did not return are untouched`() {
        val other = Station(id = "imported:x", name = "Mine", url = "https://x.example")
        val third = saved.copy(id = "c", name = "Third")

        val result = SavedStationRefresh.merge(listOf(other, saved, third), listOf(fetched))

        assertThat(result.stations.map { it.id }).containsExactly("imported:x", "a", "c")
        assertThat(result.stations[0]).isSameInstanceAs(other)
        assertThat(result.stations[2]).isSameInstanceAs(third)
        assertThat(result.updated).isEqualTo(1)
    }

    @Test
    fun `nothing changed returns the saved list itself`() {
        val current = saved.copy(urlResolved = fetched.urlResolved, hls = 1, bitrate = 128, codec = "AAC")
        val list = listOf(current)

        val result = SavedStationRefresh.merge(list, listOf(fetched))

        assertThat(result.updated).isEqualTo(0)
        assertThat(result.stations).isSameInstanceAs(list)
    }

    @Test
    fun `an unplayable directory entry does not blank the stream`() {
        val result = SavedStationRefresh.merge(listOf(saved), listOf(fetched.copy(urlResolved = "", url = "")))

        assertThat(result.stations.single()).isEqualTo(saved)
    }

    @Test
    fun `empty inputs are a no-op`() {
        assertThat(SavedStationRefresh.merge(emptyList(), listOf(fetched)).updated).isEqualTo(0)
        assertThat(SavedStationRefresh.merge(listOf(saved), emptyList()).stations).containsExactly(saved)
    }
}
