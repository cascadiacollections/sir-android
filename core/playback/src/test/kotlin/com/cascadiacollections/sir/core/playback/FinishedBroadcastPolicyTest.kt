package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class FinishedBroadcastPolicyTest {

    @Test
    fun `a file of known duration is finite`() {
        assertThat(FinishedBroadcastPolicy.isFinite(isLive = false, isDynamic = false, durationMs = 300_000L)).isTrue()
    }

    @Test
    fun `live, dynamic or unknown-length media is not finite`() {
        assertThat(FinishedBroadcastPolicy.isFinite(isLive = true, isDynamic = false, durationMs = 300_000L)).isFalse()
        assertThat(FinishedBroadcastPolicy.isFinite(isLive = false, isDynamic = true, durationMs = 300_000L)).isFalse()
        assertThat(FinishedBroadcastPolicy.isFinite(isLive = false, isDynamic = false, durationMs = null)).isFalse()
        assertThat(FinishedBroadcastPolicy.isFinite(isLive = false, isDynamic = false, durationMs = 0L)).isFalse()
    }

    @Test
    fun `a live stream that ends is rejoined whatever the setting`() {
        assertThat(FinishedBroadcastPolicy.onEnded(isFinite = false, loopFinishedBroadcasts = false))
            .isEqualTo(EndOfStreamAction.REJOIN)
        assertThat(FinishedBroadcastPolicy.onEnded(isFinite = false, loopFinishedBroadcasts = true))
            .isEqualTo(EndOfStreamAction.REJOIN)
    }

    @Test
    fun `a finished broadcast stops by default and loops when asked`() {
        assertThat(FinishedBroadcastPolicy.onEnded(isFinite = true, loopFinishedBroadcasts = false))
            .isEqualTo(EndOfStreamAction.STOP)
        assertThat(FinishedBroadcastPolicy.onEnded(isFinite = true, loopFinishedBroadcasts = true))
            .isEqualTo(EndOfStreamAction.LOOP)
    }

    @Test
    fun `only a finite item with looping on repeats itself`() {
        assertThat(FinishedBroadcastPolicy.repeatsCurrentItem(isFinite = true, loopFinishedBroadcasts = true)).isTrue()
        assertThat(FinishedBroadcastPolicy.repeatsCurrentItem(isFinite = true, loopFinishedBroadcasts = false))
            .isFalse()
        assertThat(FinishedBroadcastPolicy.repeatsCurrentItem(isFinite = false, loopFinishedBroadcasts = true))
            .isFalse()
    }
}
