package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class StallCeilingTest {

    @Test
    fun `a token is current immediately after arming`() {
        val ceiling = StallCeiling()

        assertThat(ceiling.isCurrent(ceiling.arm())).isTrue()
    }

    @Test
    fun `clearing invalidates the armed token`() {
        val ceiling = StallCeiling()
        val token = ceiling.arm()

        ceiling.clear()

        assertThat(ceiling.isCurrent(token)).isFalse()
    }

    @Test
    fun `re-arming invalidates the previous token`() {
        val ceiling = StallCeiling()
        val first = ceiling.arm()

        val second = ceiling.arm()

        assertThat(ceiling.isCurrent(first)).isFalse()
        assertThat(ceiling.isCurrent(second)).isTrue()
    }

    @Test
    fun `clearing before ever arming leaves a later arm current`() {
        val ceiling = StallCeiling()

        ceiling.clear()
        val token = ceiling.arm()

        assertThat(ceiling.isCurrent(token)).isTrue()
    }

    @Test
    fun `the default timeout is ShoutKit's 30 seconds`() {
        assertThat(StallCeiling().timeoutDelayMs).isEqualTo(30_000L)
    }

    @Test
    fun `a custom timeout is honored`() {
        assertThat(StallCeiling(timeoutMs = 5_000L).timeoutDelayMs).isEqualTo(5_000L)
    }
}
