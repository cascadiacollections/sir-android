package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRecoveryTest {

    private val recovery = StreamRecovery()

    @Test
    fun `a transient failure reconnects three times at 2, 4 and 8 seconds, then fails`() {
        val decisions = List(4) {
            recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = true)
        }

        assertEquals(
            listOf(
                RecoveryDecision.Reconnect(2_000L),
                RecoveryDecision.Reconnect(4_000L),
                RecoveryDecision.Reconnect(8_000L),
                RecoveryDecision.Fail(StreamFailure.NoNetwork)
            ),
            decisions
        )
    }

    @Test
    fun `retry after giving up gets the whole budget again`() {
        repeat(4) { recovery.onFailure(StreamFailure.Transient, playbackWanted = true) }

        assertEquals(
            RecoveryDecision.Reconnect(2_000L),
            recovery.onFailure(StreamFailure.Transient, playbackWanted = true)
        )
    }

    @Test
    fun `permanent failures fail at once without spending the budget`() {
        listOf(
            StreamFailure.StationUnavailable(404),
            StreamFailure.Unplayable,
            StreamFailure.Stalled
        ).forEach { failure ->
            assertEquals(RecoveryDecision.Fail(failure), recovery.onFailure(failure, playbackWanted = true))
        }
        assertEquals(
            RecoveryDecision.Reconnect(2_000L),
            recovery.onFailure(StreamFailure.Transient, playbackWanted = true)
        )
    }

    @Test
    fun `a failure nobody asked to hear is ignored, not reported as reconnecting`() {
        // Regression: a cold launch offline with the last station paused prepared the player,
        // the load failed, and the service scheduled a reconnect and published "retrying" —
        // so the UI showed "Reconnecting…" and Cancel for a stream the listener never started.
        assertEquals(
            RecoveryDecision.Ignore,
            recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = false)
        )
        assertEquals(RecoveryDecision.Ignore, recovery.onFailure(StreamFailure.Unplayable, playbackWanted = false))
    }

    @Test
    fun `an ignored failure leaves the full budget for the play that follows`() {
        recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = true)
        recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = false)

        assertEquals(
            RecoveryDecision.Reconnect(2_000L),
            recovery.onFailure(StreamFailure.NoNetwork, playbackWanted = true)
        )
    }

    @Test
    fun `recovering resets the schedule`() {
        recovery.onFailure(StreamFailure.Transient, playbackWanted = true)
        recovery.onFailure(StreamFailure.Transient, playbackWanted = true)

        recovery.onRecovered()

        assertEquals(
            RecoveryDecision.Reconnect(2_000L),
            recovery.onFailure(StreamFailure.Transient, playbackWanted = true)
        )
    }

    @Test
    fun `the combined budget is explicit and does not multiply`() {
        // Media3 retries nothing itself, so one drop costs at most the first attempt plus
        // the three reconnects, with 14 s of scheduled backoff between them.
        assertEquals(0, ReconnectBudget.LOAD_RETRIES_PER_CONNECTION)
        assertEquals(4, ReconnectBudget.MAX_CONNECTION_ATTEMPTS)
        val scheduled = generateSequence {
            (recovery.onFailure(StreamFailure.Transient, playbackWanted = true) as? RecoveryDecision.Reconnect)?.delayMs
        }.toList()
        assertEquals(ReconnectBudget.MAX_CONNECTION_ATTEMPTS - 1, scheduled.size)
        assertEquals(14_000L, scheduled.sum())
    }
}
