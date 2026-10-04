package com.cascadiacollections.sir.core.directory

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import java.io.IOException
import kotlinx.coroutines.test.runTest
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

        assertThat(provider.mirrors())
            .containsExactly("https://de1.api.radio-browser.info", "https://fi1.api.radio-browser.info", all)
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

        assertThat(provider.mirrors()).containsExactly("https://de1.api.radio-browser.info", all)
    }

    @Test
    fun `mirrors are shuffled per request`() = runTest {
        val source = CountingSource { listOf("a.example", "b.example") }
        val provider = DiscoveringMirrorProvider(source, shuffle = { it.reversed() })

        assertThat(provider.mirrors()).containsExactly("https://b.example", "https://a.example", all)
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
        assertThat(source.calls).isEqualTo(1)

        now = 1_000
        provider.mirrors()
        assertThat(source.calls).isEqualTo(2)
    }

    @Test
    fun `the default ttl is a day`() {
        assertThat(DiscoveringMirrorProvider.DEFAULT_TTL_MILLIS).isEqualTo(24 * 60 * 60 * 1000L)
    }

    @Test
    fun `failed discovery falls back to the hard-coded list`() = runTest {
        val source = CountingSource { throw IOException("dns") }
        val provider = DiscoveringMirrorProvider(source, shuffle = { it })

        val mirrors = provider.mirrors()

        assertThat(mirrors.toSet()).isEqualTo(RotatingMirrorProvider.DEFAULT_MIRRORS.toSet())
        assertThat(mirrors.last()).isEqualTo(all)
        assertThat(mirrors.count { it == all }).isEqualTo(1)
    }

    @Test
    fun `an empty discovery result falls back too`() = runTest {
        val provider = DiscoveringMirrorProvider(
            CountingSource { emptyList() },
            fallback = listOf("https://x.example"),
            shuffle = { it }
        )

        assertThat(provider.mirrors()).containsExactly("https://x.example", all)
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
        assertThat(source.calls).isEqualTo(1)

        source.answer = { listOf("up.example") }
        now = 100
        assertThat(provider.mirrors()).containsExactly("https://up.example", all)
        assertThat(source.calls).isEqualTo(2)
    }

    @Test
    fun `base url conversion`() {
        assertThat(DiscoveringMirrorProvider.toBaseUrl(" nl1.api.radio-browser.info "))
            .isEqualTo("https://nl1.api.radio-browser.info")
        assertThat(DiscoveringMirrorProvider.toBaseUrl("https://nl1.api.radio-browser.info")).isNull()
        assertThat(DiscoveringMirrorProvider.toBaseUrl("host:8080")).isNull()
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

        assertThat(source.serverNames())
            .containsExactly("de1.api.radio-browser.info", "de1.api.radio-browser.info", "at1.api.radio-browser.info")
        val request = transport.requests.single()
        assertThat(request.url.toString()).isEqualTo("https://all.api.radio-browser.info/json/servers")
        assertThat(request.header("User-Agent")).isEqualTo("UA/1")
    }

    @Test
    fun `server list source surfaces http and decode failures as io errors`() {
        val failing = RadioBrowserServerListSource(FakeTransport { Reply.Http(code = 500) }.client)
        assertFailure { failing.serverNames() }.isInstanceOf<HttpStatusException>()

        val garbage = RadioBrowserServerListSource(
            FakeTransport {
                Reply.Http(body = "<html>")
            }.client
        )
        assertFailure { garbage.serverNames() }.isInstanceOf<IOException>()
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

        assertThat(provider.mirrors()).containsExactly("https://fallback.example", all)
    }
}
