package com.cascadiacollections.sir.core.directory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotatingMirrorProviderTest {

    @Test
    fun `returns every configured mirror`() {
        val provider = RotatingMirrorProvider(shuffle = { it })

        assertEquals(RotatingMirrorProvider.DEFAULT_MIRRORS, provider.mirrors())
    }

    @Test
    fun `rotation preserves the full mirror set`() {
        val provider = RotatingMirrorProvider(shuffle = { it.reversed() })

        assertEquals(
            RotatingMirrorProvider.DEFAULT_MIRRORS.toSet(),
            provider.mirrors().toSet()
        )
    }

    @Test
    fun `all default mirrors are https`() {
        assertTrue(RotatingMirrorProvider.DEFAULT_MIRRORS.all { it.startsWith("https://") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty mirror list is rejected`() {
        RotatingMirrorProvider(mirrors = emptyList())
    }
}
