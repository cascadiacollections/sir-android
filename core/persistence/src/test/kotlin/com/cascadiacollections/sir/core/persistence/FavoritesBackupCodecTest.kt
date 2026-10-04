package com.cascadiacollections.sir.core.persistence

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.cascadiacollections.sir.core.model.Station
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class FavoritesBackupCodecTest {

    /** Verbatim shape of a ShoutKit export: `JSONEncoder` with prettyPrinted + sortedKeys. */
    private val shoutKitExport = """
        {
          "favorites" : [
            {
              "artworkURL" : "https://cdn.example/kexp.png",
              "genre" : "Indie",
              "id" : "9617a958-0601-11e8-ae97-52543be04c81",
              "name" : "KEXP 90.3",
              "sortIndex" : 0,
              "streamURL" : "https://kexp.streamguys1.com/kexp160.aac"
            },
            {
              "genre" : "",
              "id" : "no-stream",
              "name" : "Unresolved",
              "sortIndex" : 1
            },
            {
              "genre" : "Jazz",
              "id" : "961a2c6c-0601-11e8-ae97-52543be04c81",
              "name" : "KNKX",
              "sortIndex" : -1,
              "streamURL" : "https://live.wostreaming.net/direct/ppm-knkxfm-ibc1"
            }
          ],
          "schemaVersion" : 1
        }
    """.trimIndent()

    @Test
    fun `decodes a ShoutKit export in sortIndex order with the field mapping`() {
        val stations = FavoritesBackupCodec.decode(shoutKitExport)

        assertThat(stations.map { it.id }).containsExactly(
            "961a2c6c-0601-11e8-ae97-52543be04c81",
            "9617a958-0601-11e8-ae97-52543be04c81",
            "no-stream"
        )
        assertThat(stations[2].isPlayable, name = "kept for the importer to re-resolve").isFalse()
        val kexp = stations[1]
        assertThat(kexp.name).isEqualTo("KEXP 90.3")
        assertThat(kexp.url).isEqualTo("https://kexp.streamguys1.com/kexp160.aac")
        assertThat(kexp.favicon).isEqualTo("https://cdn.example/kexp.png")
        assertThat(kexp.tags).isEqualTo("Indie")
        assertThat(stations[0].favicon).isNull()
    }

    @Test
    fun `encode writes the ShoutKit schema with first tag as genre and sortIndex as position`() {
        val text = FavoritesBackupCodec.encode(
            listOf(
                Station(
                    id = "a",
                    name = "A",
                    url = "https://a/stream",
                    favicon = "https://a/icon.png",
                    tags = "rock, indie"
                ),
                Station(id = "b", name = "B", url = "https://b/stream")
            )
        )
        val root = Json.parseToJsonElement(text).jsonObject
        assertThat(root.getValue("schemaVersion").jsonPrimitive.int).isEqualTo(1)
        val favorites = root.getValue("favorites").jsonArray.map { it.jsonObject }
        assertThat(favorites[0].getValue("id").jsonPrimitive.content).isEqualTo("a")
        assertThat(favorites[0].getValue("streamURL").jsonPrimitive.content).isEqualTo("https://a/stream")
        assertThat(favorites[0].getValue("artworkURL").jsonPrimitive.content).isEqualTo("https://a/icon.png")
        assertThat(favorites[0].getValue("genre").jsonPrimitive.content).isEqualTo("rock")
        assertThat(favorites.map { it.getValue("sortIndex").jsonPrimitive.int })
            .containsExactly(0, 1)
        assertThat(favorites[1].keys, name = "absent artwork is omitted, as ShoutKit does").doesNotContain("artworkURL")
        assertThat(favorites[1].getValue("genre").jsonPrimitive.content).isEmpty()
    }

    @Test
    fun `encode writes the resolved stream so ShoutKit never receives a playlist url`() {
        val text = FavoritesBackupCodec.encode(
            listOf(Station(id = "a", name = "A", url = "https://a/listen.pls", urlResolved = "https://a/stream.mp3"))
        )
        val favorite = Json.parseToJsonElement(text).jsonObject.getValue("favorites").jsonArray[0].jsonObject
        assertThat(favorite.getValue("streamURL").jsonPrimitive.content).isEqualTo("https://a/stream.mp3")
    }

    @Test
    fun `entries without a stream url are kept for re-resolution`() {
        val text = """{"schemaVersion":1,"favorites":[
            {"id":"96062a7b-0601-11e8-ae97-52543be04c81","name":"Jazz","genre":"","sortIndex":0}]}"""
        val station = FavoritesBackupCodec.decode(text).single()
        assertThat(station.id).isEqualTo("96062a7b-0601-11e8-ae97-52543be04c81")
        assertThat(station.url).isEmpty()
        assertThat(station.isPlayable).isFalse()
    }

    @Test
    fun `a nameless entry with a blank stream url falls back to its id`() {
        val text = """{"schemaVersion":1,"favorites":[{"id":"abc","name":"","streamURL":"","genre":"","sortIndex":0}]}"""
        assertThat(FavoritesBackupCodec.decode(text).single().name).isEqualTo("abc")
    }

    @Test
    fun `round trip preserves order and mapped fields`() {
        val stations = listOf(
            Station(id = "x", name = "X", url = "https://x/s", favicon = "https://x/f", tags = "jazz"),
            Station(id = "y", name = "Y", url = "https://y/s")
        )
        assertThat(FavoritesBackupCodec.decode(FavoritesBackupCodec.encode(stations))).isEqualTo(stations)
    }

    @Test
    fun `round trip of a ShoutKit export is stable`() {
        val once = FavoritesBackupCodec.decode(shoutKitExport)
        assertThat(FavoritesBackupCodec.decode(FavoritesBackupCodec.encode(once))).isEqualTo(once)
    }

    @Test
    fun `duplicate and blank ids keep only the first valid entry`() {
        val text = """{"schemaVersion":1,"favorites":[
            {"id":"a","name":"first","streamURL":"https://a/1","genre":"","sortIndex":0},
            {"id":"a","name":"second","streamURL":"https://a/2","genre":"","sortIndex":1},
            {"id":"","name":"blank","streamURL":"https://b","genre":"","sortIndex":2}]}"""
        assertThat(FavoritesBackupCodec.decode(text).map { it.name }).containsExactly("first")
    }

    @Test
    fun `an unknown schema version is rejected`() {
        assertFailure {
            FavoritesBackupCodec.decode("""{"schemaVersion":2,"favorites":[]}""")
        }.isInstanceOf<FavoritesBackupCodec.UnsupportedSchemaException>()
            .prop(FavoritesBackupCodec.UnsupportedSchemaException::schemaVersion)
            .isEqualTo(2)
    }

    @Test
    fun `non-backup text fails to decode`() {
        assertFailure {
            FavoritesBackupCodec.decode("#EXTM3U\nhttps://a")
        }.isInstanceOf<SerializationException>()
    }

    @Test
    fun `detects backups by content before extension`() {
        assertThat(
            FavoritesBackupCodec.looksLikeBackup("﻿  {\"schemaVersion\":1}", "favorites.txt")
        ).isTrue()
        assertThat(FavoritesBackupCodec.looksLikeBackup("#EXTM3U", "list.json")).isFalse()
        assertThat(FavoritesBackupCodec.looksLikeBackup("[playlist]\nFile1=x", null)).isFalse()
        assertThat(FavoritesBackupCodec.looksLikeBackup("", "shoutkit-favorites.json")).isTrue()
        assertThat(FavoritesBackupCodec.looksLikeBackup("https://a/stream", "list.m3u")).isFalse()
    }
}
