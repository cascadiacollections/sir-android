package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class StreamEndpointsTest {

    @Test
    fun `classifies by url path`() {
        assertThat(StreamEndpoints.classify("http://example.com:8000/stream")).isEqualTo(StreamUrlKind.DIRECT)
        assertThat(StreamEndpoints.classify("https://example.com/live.mp3")).isEqualTo(StreamUrlKind.DIRECT)
        assertThat(StreamEndpoints.classify("https://example.com/live/master.m3u8")).isEqualTo(StreamUrlKind.HLS)
        assertThat(StreamEndpoints.classify("https://example.com/LIVE.M3U8?token=a.pls")).isEqualTo(StreamUrlKind.HLS)
        assertThat(StreamEndpoints.classify("http://example.com/listen.pls")).isEqualTo(StreamUrlKind.PLAYLIST)
        assertThat(StreamEndpoints.classify("http://example.com/listen.m3u?sid=1")).isEqualTo(StreamUrlKind.PLAYLIST)
        assertThat(StreamEndpoints.classify("http://example.com/tunein.PLS#frag")).isEqualTo(StreamUrlKind.PLAYLIST)
    }

    @Test
    fun `query and fragment do not decide the kind`() {
        assertThat(StreamEndpoints.classify("http://example.com/stream?format=.m3u")).isEqualTo(StreamUrlKind.DIRECT)
        assertThat(StreamEndpoints.classify("http://example.com/stream#x.m3u8")).isEqualTo(StreamUrlKind.DIRECT)
    }

    @Test
    fun `unparseable urls fall back to a plain path split`() {
        assertThat(StreamEndpoints.classify("http://example.com/a b/listen.pls?x=1")).isEqualTo(StreamUrlKind.PLAYLIST)
    }

    @Test
    fun `hls flag overrides the path`() {
        assertThat(StreamEndpoints.classify("https://example.com/live", hlsHint = true)).isEqualTo(StreamUrlKind.HLS)
        assertThat(StreamEndpoints.withoutFetch("https://example.com/live", hlsHint = true))
            .isEqualTo(StreamEndpoint("https://example.com/live", isHls = true))
    }

    @Test
    fun `direct and hls urls need no fetch`() {
        assertThat(StreamEndpoints.withoutFetch("http://example.com/s"))
            .isEqualTo(StreamEndpoint("http://example.com/s"))
        assertThat(StreamEndpoints.withoutFetch("https://example.com/a.m3u8"))
            .isEqualTo(StreamEndpoint("https://example.com/a.m3u8", isHls = true))
        assertThat(StreamEndpoints.withoutFetch("http://example.com/listen.pls")).isNull()
    }

    @Test
    fun `playlist entry becomes the endpoint`() {
        val endpoint = StreamEndpoints.fromPlaylist(
            "http://example.com/listen.pls",
            "[playlist]\nFile1=http://example.com:8000/stream\n"
        )

        assertThat(endpoint).isEqualTo(StreamEndpoint("http://example.com:8000/stream"))
    }

    @Test
    fun `playlist entry pointing at hls is opened as hls`() {
        val endpoint = StreamEndpoints.fromPlaylist("http://example.com/x.m3u", "https://cdn.example.com/a.m3u8\n")

        assertThat(endpoint).isEqualTo(StreamEndpoint("https://cdn.example.com/a.m3u8", isHls = true))
    }

    @Test
    fun `hls body keeps the original url as hls`() {
        val endpoint = StreamEndpoints.fromPlaylist("https://example.com/x.m3u", "#EXTM3U\n#EXT-X-VERSION:3\n")

        assertThat(endpoint).isEqualTo(StreamEndpoint("https://example.com/x.m3u", isHls = true))
    }

    @Test
    fun `failed or empty fetch falls back to the original url`() {
        assertThat(StreamEndpoints.fromPlaylist("http://example.com/listen.pls", null))
            .isEqualTo(StreamEndpoint("http://example.com/listen.pls"))
        assertThat(StreamEndpoints.fromPlaylist("http://example.com/listen.m3u", "garbage"))
            .isEqualTo(StreamEndpoint("http://example.com/listen.m3u"))
    }
}
