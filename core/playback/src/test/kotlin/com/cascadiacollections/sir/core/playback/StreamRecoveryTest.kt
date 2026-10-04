package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.Test

class StreamRecoveryTest {

    private val recovery = StreamRecovery()

    @Test
    fun `a transient failure reconnects three times at 2, 4 and 8 seconds, then fails`() {
        val decisions = List(4) {
            recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = true)
        }

        assertThat(decisions).containsExactly(
            RecoveryDecision.Reconnect(2_000L),
            RecoveryDecision.Reconnect(4_000L),
            RecoveryDecision.Reconnect(8_000L),
            RecoveryDecision.Fail(StreamFailure.NoNetwork)
        )
    }

    @Test
    fun `retry after giving up gets the whole budget again`() {
        repeat(4) { recovery.onFailure(StreamFailure.Transient, playbackWanted = true) }

        assertThat(recovery.onFailure(StreamFailure.Transient, playbackWanted = true))
            .isEqualTo(RecoveryDecision.Reconnect(2_000L))
    }

    @Test
    fun `permanent failures fail at once without spending the budget`() {
        listOf(
            StreamFailure.StationUnavailable(404),
            StreamFailure.Unplayable,
            StreamFailure.Stalled
        ).forEach { failure ->
            assertThat(recovery.onFailure(failure, playbackWanted = true)).isEqualTo(RecoveryDecision.Fail(failure))
        }
        assertThat(recovery.onFailure(StreamFailure.Transient, playbackWanted = true))
            .isEqualTo(RecoveryDecision.Reconnect(2_000L))
    }

    @Test
    fun `a failure nobody asked to hear is ignored, not reported as reconnecting`() {
        // Regression: a cold launch offline with the last station paused prepared the player,
        // the load failed, and the service scheduled a reconnect and published "retrying" —
        // so the UI showed "Reconnecting…" and Cancel for a stream the listener never started.
        assertThat(recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = false))
            .isEqualTo(RecoveryDecision.Ignore)
        assertThat(recovery.onFailure(StreamFailure.Unplayable, playbackWanted = false))
            .isEqualTo(RecoveryDecision.Ignore)
    }

    @Test
    fun `an ignored failure leaves the full budget for the play that follows`() {
        recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = true)
        recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = false)

        assertThat(recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = true))
            .isEqualTo(RecoveryDecision.Reconnect(2_000L))
    }

    @Test
    fun `recovering resets the schedule`() {
        recovery.onFailure(StreamFailure.Transient, playbackWanted = true)
        recovery.onFailure(StreamFailure.Transient, playbackWanted = true)

        recovery.onRecovered()

        assertThat(recovery.onFailure(StreamFailure.Transient, playbackWanted = true))
            .isEqualTo(RecoveryDecision.Reconnect(2_000L))
    }

    @Test
    fun `the combined budget is explicit and does not multiply`() {
        // Media3 retries nothing itself, so one drop costs at most the first attempt plus
        // the three reconnects, with 14 s of scheduled backoff between them.
        assertThat(ReconnectBudget.LOAD_RETRIES_PER_CONNECTION).isEqualTo(0)
        assertThat(ReconnectBudget.MAX_CONNECTION_ATTEMPTS).isEqualTo(4)
        val scheduled = generateSequence {
            (recovery.onFailure(StreamFailure.Transient, playbackWanted = true) as? RecoveryDecision.Reconnect)?.delayMs
        }.toList()
        assertThat(scheduled.size).isEqualTo(ReconnectBudget.MAX_CONNECTION_ATTEMPTS - 1)
        assertThat(scheduled.sum()).isEqualTo(14_000L)
    }
}
