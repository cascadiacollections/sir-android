package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StallCeilingTest {

    @Test
    fun `a token is current immediately after arming`() {
        val ceiling = StallCeiling()

        assertTrue(ceiling.isCurrent(ceiling.arm()))
    }

    @Test
    fun `clearing invalidates the armed token`() {
        val ceiling = StallCeiling()
        val token = ceiling.arm()

        ceiling.clear()

        assertFalse(ceiling.isCurrent(token))
    }

    @Test
    fun `re-arming invalidates the previous token`() {
        val ceiling = StallCeiling()
        val first = ceiling.arm()

        val second = ceiling.arm()

        assertFalse(ceiling.isCurrent(first))
        assertTrue(ceiling.isCurrent(second))
    }

    @Test
    fun `clearing before ever arming leaves a later arm current`() {
        val ceiling = StallCeiling()

        ceiling.clear()
        val token = ceiling.arm()

        assertTrue(ceiling.isCurrent(token))
    }

    @Test
    fun `the default timeout is 90 seconds`() {
        assertEquals(90_000L, StallCeiling().timeoutDelayMs)
    }

    @Test
    fun `a custom timeout is honored`() {
        assertEquals(5_000L, StallCeiling(timeoutMs = 5_000L).timeoutDelayMs)
    }
}
