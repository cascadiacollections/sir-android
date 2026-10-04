package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

        assertEquals(
            listOf("961a2c6c-0601-11e8-ae97-52543be04c81", "9617a958-0601-11e8-ae97-52543be04c81", "no-stream"),
            stations.map { it.id }
        )
        assertFalse("kept for the importer to re-resolve", stations[2].isPlayable)
        val kexp = stations[1]
        assertEquals("KEXP 90.3", kexp.name)
        assertEquals("https://kexp.streamguys1.com/kexp160.aac", kexp.url)
        assertEquals("https://cdn.example/kexp.png", kexp.favicon)
        assertEquals("Indie", kexp.tags)
        assertEquals(null, stations[0].favicon)
    }

    @Test
    fun `encode writes the ShoutKit schema with first tag as genre and sortIndex as position`() {
        val text = FavoritesBackupCodec.encode(
            listOf(
                Station(id = "a", name = "A", url = "https://a/stream", favicon = "https://a/icon.png", tags = "rock, indie"),
                Station(id = "b", name = "B", url = "https://b/stream"),
            )
        )
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(1, root.getValue("schemaVersion").jsonPrimitive.int)
        val favorites = root.getValue("favorites").jsonArray.map { it.jsonObject }
        assertEquals("a", favorites[0].getValue("id").jsonPrimitive.content)
        assertEquals("https://a/stream", favorites[0].getValue("streamURL").jsonPrimitive.content)
        assertEquals("https://a/icon.png", favorites[0].getValue("artworkURL").jsonPrimitive.content)
        assertEquals("rock", favorites[0].getValue("genre").jsonPrimitive.content)
        assertEquals(listOf(0, 1), favorites.map { it.getValue("sortIndex").jsonPrimitive.int })
        assertFalse("absent artwork is omitted, as ShoutKit does", "artworkURL" in favorites[1])
        assertEquals("", favorites[1].getValue("genre").jsonPrimitive.content)
    }

    @Test
    fun `encode writes the resolved stream so ShoutKit never receives a playlist url`() {
        val text = FavoritesBackupCodec.encode(
            listOf(Station(id = "a", name = "A", url = "https://a/listen.pls", urlResolved = "https://a/stream.mp3"))
        )
        val favorite = Json.parseToJsonElement(text).jsonObject.getValue("favorites").jsonArray[0].jsonObject
        assertEquals("https://a/stream.mp3", favorite.getValue("streamURL").jsonPrimitive.content)
    }

    @Test
    fun `entries without a stream url are kept for re-resolution`() {
        val text = """{"schemaVersion":1,"favorites":[
            {"id":"96062a7b-0601-11e8-ae97-52543be04c81","name":"Jazz","genre":"","sortIndex":0}]}"""
        val station = FavoritesBackupCodec.decode(text).single()
        assertEquals("96062a7b-0601-11e8-ae97-52543be04c81", station.id)
        assertEquals("", station.url)
        assertFalse(station.isPlayable)
    }

    @Test
    fun `round trip preserves order and mapped fields`() {
        val stations = listOf(
            Station(id = "x", name = "X", url = "https://x/s", favicon = "https://x/f", tags = "jazz"),
            Station(id = "y", name = "Y", url = "https://y/s"),
        )
        assertEquals(stations, FavoritesBackupCodec.decode(FavoritesBackupCodec.encode(stations)))
    }

    @Test
    fun `round trip of a ShoutKit export is stable`() {
        val once = FavoritesBackupCodec.decode(shoutKitExport)
        assertEquals(once, FavoritesBackupCodec.decode(FavoritesBackupCodec.encode(once)))
    }

    @Test
    fun `duplicate and blank ids keep only the first valid entry`() {
        val text = """{"schemaVersion":1,"favorites":[
            {"id":"a","name":"first","streamURL":"https://a/1","genre":"","sortIndex":0},
            {"id":"a","name":"second","streamURL":"https://a/2","genre":"","sortIndex":1},
            {"id":"","name":"blank","streamURL":"https://b","genre":"","sortIndex":2}]}"""
        assertEquals(listOf("first"), FavoritesBackupCodec.decode(text).map { it.name })
    }

    @Test
    fun `an unknown schema version is rejected`() {
        val error = assertThrows(FavoritesBackupCodec.UnsupportedSchemaException::class.java) {
            FavoritesBackupCodec.decode("""{"schemaVersion":2,"favorites":[]}""")
        }
        assertEquals(2, error.schemaVersion)
    }

    @Test
    fun `non-backup text fails to decode`() {
        assertThrows(SerializationException::class.java) { FavoritesBackupCodec.decode("#EXTM3U\nhttps://a") }
    }

    @Test
    fun `detects backups by content before extension`() {
        assertTrue(FavoritesBackupCodec.looksLikeBackup("﻿  {\"schemaVersion\":1}", "favorites.txt"))
        assertFalse(FavoritesBackupCodec.looksLikeBackup("#EXTM3U", "list.json"))
        assertFalse(FavoritesBackupCodec.looksLikeBackup("[playlist]\nFile1=x", null))
        assertTrue(FavoritesBackupCodec.looksLikeBackup("", "shoutkit-favorites.json"))
        assertFalse(FavoritesBackupCodec.looksLikeBackup("https://a/stream", "list.m3u"))
    }
}
