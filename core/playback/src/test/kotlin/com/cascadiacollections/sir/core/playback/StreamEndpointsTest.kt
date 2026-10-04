package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamEndpointsTest {

    @Test
    fun `classifies by url path`() {
        assertEquals(StreamUrlKind.DIRECT, StreamEndpoints.classify("http://example.com:8000/stream"))
        assertEquals(StreamUrlKind.DIRECT, StreamEndpoints.classify("https://example.com/live.mp3"))
        assertEquals(StreamUrlKind.HLS, StreamEndpoints.classify("https://example.com/live/master.m3u8"))
        assertEquals(StreamUrlKind.HLS, StreamEndpoints.classify("https://example.com/LIVE.M3U8?token=a.pls"))
        assertEquals(StreamUrlKind.PLAYLIST, StreamEndpoints.classify("http://example.com/listen.pls"))
        assertEquals(StreamUrlKind.PLAYLIST, StreamEndpoints.classify("http://example.com/listen.m3u?sid=1"))
        assertEquals(StreamUrlKind.PLAYLIST, StreamEndpoints.classify("http://example.com/tunein.PLS#frag"))
    }

    @Test
    fun `query and fragment do not decide the kind`() {
        assertEquals(StreamUrlKind.DIRECT, StreamEndpoints.classify("http://example.com/stream?format=.m3u"))
        assertEquals(StreamUrlKind.DIRECT, StreamEndpoints.classify("http://example.com/stream#x.m3u8"))
    }

    @Test
    fun `unparseable urls fall back to a plain path split`() {
        assertEquals(StreamUrlKind.PLAYLIST, StreamEndpoints.classify("http://example.com/a b/listen.pls?x=1"))
    }

    @Test
    fun `hls flag overrides the path`() {
        assertEquals(StreamUrlKind.HLS, StreamEndpoints.classify("https://example.com/live", hlsHint = true))
        assertEquals(
            StreamEndpoint("https://example.com/live", isHls = true),
            StreamEndpoints.withoutFetch("https://example.com/live", hlsHint = true)
        )
    }

    @Test
    fun `direct and hls urls need no fetch`() {
        assertEquals(StreamEndpoint("http://example.com/s"), StreamEndpoints.withoutFetch("http://example.com/s"))
        assertEquals(
            StreamEndpoint("https://example.com/a.m3u8", isHls = true),
            StreamEndpoints.withoutFetch("https://example.com/a.m3u8")
        )
        assertNull(StreamEndpoints.withoutFetch("http://example.com/listen.pls"))
    }

    @Test
    fun `playlist entry becomes the endpoint`() {
        val endpoint = StreamEndpoints.fromPlaylist(
            "http://example.com/listen.pls",
            "[playlist]\nFile1=http://example.com:8000/stream\n"
        )

        assertEquals(StreamEndpoint("http://example.com:8000/stream"), endpoint)
    }

    @Test
    fun `playlist entry pointing at hls is opened as hls`() {
        val endpoint = StreamEndpoints.fromPlaylist("http://example.com/x.m3u", "https://cdn.example.com/a.m3u8\n")

        assertEquals(StreamEndpoint("https://cdn.example.com/a.m3u8", isHls = true), endpoint)
    }

    @Test
    fun `hls body keeps the original url as hls`() {
        val endpoint = StreamEndpoints.fromPlaylist("https://example.com/x.m3u", "#EXTM3U\n#EXT-X-VERSION:3\n")

        assertEquals(StreamEndpoint("https://example.com/x.m3u", isHls = true), endpoint)
    }

    @Test
    fun `failed or empty fetch falls back to the original url`() {
        assertEquals(
            StreamEndpoint("http://example.com/listen.pls"),
            StreamEndpoints.fromPlaylist("http://example.com/listen.pls", null)
        )
        assertEquals(
            StreamEndpoint("http://example.com/listen.m3u"),
            StreamEndpoints.fromPlaylist("http://example.com/listen.m3u", "garbage")
        )
    }
}
