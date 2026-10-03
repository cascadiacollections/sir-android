package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamPlaylistParserTest {

    @Test
    fun `pls yields its first file entry`() {
        val pls = """
            [playlist]
            NumberOfEntries=2
            File2=http://backup.example.com:8000/stream
            Title2=Backup
            File1=http://primary.example.com:8000/stream
            Title1=Primary
            Length1=-1
            Version=2
        """.trimIndent()

        assertEquals(PlaylistContent.Entry("http://primary.example.com:8000/stream"), StreamPlaylistParser.parse(pls))
    }

    @Test
    fun `pls keys and spacing are matched leniently`() {
        val pls = "[Playlist]\r\nfile1 = https://example.com/live\r\n"

        assertEquals(PlaylistContent.Entry("https://example.com/live"), StreamPlaylistParser.parse(pls))
    }

    @Test
    fun `pls skips entries that are not http urls`() {
        val pls = "[playlist]\nFile1=rtsp://example.com/live\nFile2=http://example.com/live\n"

        assertEquals(PlaylistContent.Entry("http://example.com/live"), StreamPlaylistParser.parse(pls))
    }

    @Test
    fun `m3u yields its first non-comment entry`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Station
            http://example.com:8000/stream.mp3

            http://example.com:8000/second.mp3
        """.trimIndent()

        assertEquals(PlaylistContent.Entry("http://example.com:8000/stream.mp3"), StreamPlaylistParser.parse(m3u))
    }

    @Test
    fun `bare m3u with a byte order mark is parsed`() {
        assertEquals(
            PlaylistContent.Entry("https://example.com/live"),
            StreamPlaylistParser.parse("﻿https://example.com/live\n")
        )
    }

    @Test
    fun `m3u skips relative and non-http entries`() {
        val m3u = "#EXTM3U\nstream.mp3\nfile:///sdcard/x.mp3\nhttps://example.com/live\n"

        assertEquals(PlaylistContent.Entry("https://example.com/live"), StreamPlaylistParser.parse(m3u))
    }

    @Test
    fun `hls body under an m3u name is reported as hls`() {
        val hls = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"
            https://cdn.example.com/live/128k.m3u8
        """.trimIndent()

        assertEquals(PlaylistContent.Hls, StreamPlaylistParser.parse(hls))
    }

    @Test
    fun `empty and non-playlist bodies yield nothing`() {
        assertEquals(PlaylistContent.Empty, StreamPlaylistParser.parse(""))
        assertEquals(PlaylistContent.Empty, StreamPlaylistParser.parse("#EXTM3U\n#EXTINF:-1,Nothing\n"))
        assertEquals(PlaylistContent.Empty, StreamPlaylistParser.parse("<html><body>404</body></html>"))
    }
}
