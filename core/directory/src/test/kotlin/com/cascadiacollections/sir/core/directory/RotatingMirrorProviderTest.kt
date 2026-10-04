package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.startsWith
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RotatingMirrorProviderTest {

    @Test
    fun `returns every configured mirror`() = runTest {
        val provider = RotatingMirrorProvider(shuffle = { it })

        assertThat(provider.mirrors()).isEqualTo(RotatingMirrorProvider.DEFAULT_MIRRORS)
    }

    @Test
    fun `rotation preserves the full mirror set`() = runTest {
        val provider = RotatingMirrorProvider(shuffle = { it.reversed() })

        assertThat(provider.mirrors().toSet()).isEqualTo(RotatingMirrorProvider.DEFAULT_MIRRORS.toSet())
    }

    @Test
    fun `all default mirrors are https`() {
        assertThat(RotatingMirrorProvider.DEFAULT_MIRRORS).each { it.startsWith("https://") }
    }

    @Test
    fun `the DNS round-robin host stays on the fallback list`() {
        assertThat(RotatingMirrorProvider.DEFAULT_MIRRORS).contains(RotatingMirrorProvider.ALL_MIRRORS_HOST)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty mirror list is rejected`() {
        RotatingMirrorProvider(mirrors = emptyList())
    }
}
