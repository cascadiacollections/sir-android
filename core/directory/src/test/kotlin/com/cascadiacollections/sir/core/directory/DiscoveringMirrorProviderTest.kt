package com.cascadiacollections.sir.core.directory

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveringMirrorProviderTest {

    private val all = RotatingMirrorProvider.ALL_MIRRORS_HOST

    private class CountingSource(var answer: () -> List<String>) : ServerListSource {
        var calls = 0
        override fun serverNames(): List<String> {
            calls++
            return answer()
        }
    }

    @Test
    fun `discovered servers become https mirrors with the round-robin host last`() = runTest {
        val source = CountingSource {
            listOf("de1.api.radio-browser.info", "fi1.api.radio-browser.info")
        }
        val provider = DiscoveringMirrorProvider(source, shuffle = { it })

        assertEquals(
            listOf("https://de1.api.radio-browser.info", "https://fi1.api.radio-browser.info", all),
            provider.mirrors()
        )
    }

    @Test
    fun `duplicate and malformed names are discarded`() = runTest {
        val source = CountingSource {
            listOf(
                "de1.api.radio-browser.info",
                "DE1.api.radio-browser.info.",
                "evil.example/path?x=1",
                "",
                "no_underscores.example",
                "localhost"
            )
        }
        val provider = DiscoveringMirrorProvider(source, shuffle = { it })

        assertEquals(listOf("https://de1.api.radio-browser.info", all), provider.mirrors())
    }

    @Test
    fun `mirrors are shuffled per request`() = runTest {
        val source = CountingSource { listOf("a.example", "b.example") }
        val provider = DiscoveringMirrorProvider(source, shuffle = { it.reversed() })

        assertEquals(listOf("https://b.example", "https://a.example", all), provider.mirrors())
    }

    @Test
    fun `discovery is cached for the ttl and refreshed after it`() = runTest {
        var now = 0L
        val source = CountingSource { listOf("a.example") }
        val provider = DiscoveringMirrorProvider(source, ttlMillis = 1_000, clock = {
            now
        }, shuffle = { it })

        provider.mirrors()
        now = 999
        provider.mirrors()
        assertEquals(1, source.calls)

        now = 1_000
        provider.mirrors()
        assertEquals(2, source.calls)
    }

    @Test
    fun `the default ttl is a day`() {
        assertEquals(24 * 60 * 60 * 1000L, DiscoveringMirrorProvider.DEFAULT_TTL_MILLIS)
    }

    @Test
    fun `failed discovery falls back to the hard-coded list`() = runTest {
        val source = CountingSource { throw IOException("dns") }
        val provider = DiscoveringMirrorProvider(source, shuffle = { it })

        val mirrors = provider.mirrors()

        assertEquals(RotatingMirrorProvider.DEFAULT_MIRRORS.toSet(), mirrors.toSet())
        assertEquals(all, mirrors.last())
        assertEquals(1, mirrors.count { it == all })
    }

    @Test
    fun `an empty discovery result falls back too`() = runTest {
        val provider = DiscoveringMirrorProvider(
            CountingSource { emptyList() },
            fallback = listOf("https://x.example"),
            shuffle = { it }
        )

        assertEquals(listOf("https://x.example", all), provider.mirrors())
    }

    @Test
    fun `a failed discovery is retried only after the failure window`() = runTest {
        var now = 0L
        val source = CountingSource { throw IOException("down") }
        val provider = DiscoveringMirrorProvider(
            source,
            failureRetryMillis = 100,
            clock = { now },
            shuffle = { it }
        )

        provider.mirrors()
        provider.mirrors()
        assertEquals(1, source.calls)

        source.answer = { listOf("up.example") }
        now = 100
        assertEquals(listOf("https://up.example", all), provider.mirrors())
        assertEquals(2, source.calls)
    }

    @Test
    fun `base url conversion`() {
        assertEquals(
            "https://nl1.api.radio-browser.info",
            DiscoveringMirrorProvider.toBaseUrl(" nl1.api.radio-browser.info ")
        )
        assertNull(DiscoveringMirrorProvider.toBaseUrl("https://nl1.api.radio-browser.info"))
        assertNull(DiscoveringMirrorProvider.toBaseUrl("host:8080"))
    }

    @Test
    fun `server list source parses names and identifies itself`() {
        val transport = FakeTransport {
            Reply.Http(
                body = """[{"ip":"1.2.3.4","name":"de1.api.radio-browser.info"},""" +
                    """{"ip":"::1","name":"de1.api.radio-browser.info"},{"ip":"5.6.7.8","name":"at1.api.radio-browser.info"}]"""
            )
        }
        val source = RadioBrowserServerListSource(transport.client, userAgent = "UA/1")

        assertEquals(
            listOf("de1.api.radio-browser.info", "de1.api.radio-browser.info", "at1.api.radio-browser.info"),
            source.serverNames()
        )
        val request = transport.requests.single()
        assertEquals("https://all.api.radio-browser.info/json/servers", request.url.toString())
        assertEquals("UA/1", request.header("User-Agent"))
    }

    @Test
    fun `server list source surfaces http and decode failures as io errors`() {
        val failing = RadioBrowserServerListSource(FakeTransport { Reply.Http(code = 500) }.client)
        assertTrue(runCatching { failing.serverNames() }.exceptionOrNull() is HttpStatusException)

        val garbage = RadioBrowserServerListSource(
            FakeTransport {
                Reply.Http(body = "<html>")
            }.client
        )
        assertTrue(runCatching { garbage.serverNames() }.exceptionOrNull() is IOException)
    }

    @Test
    fun `end to end a failed discovery still reaches a fallback mirror`() = runTest {
        val transport = FakeTransport { url ->
            when {
                url.encodedPath == "/json/servers" -> Reply.Fail()
                else -> Reply.Http(body = "[]")
            }
        }
        val provider = DiscoveringMirrorProvider(
            RadioBrowserServerListSource(transport.client),
            fallback = listOf("https://fallback.example"),
            shuffle = { it }
        )

        assertEquals(listOf("https://fallback.example", all), provider.mirrors())
    }
}
