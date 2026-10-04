package com.cascadiacollections.sir.core.playback

/**
 * The fade-in applied each time audio starts — a first play, a resume, a reconnect.
 *
 * Live radio has no position to resume: every start joins the stream at whatever the live
 * edge is now, and doing that at full volume reads as a click or a jump-cut. Mirrors
 * ShoutKit's volume ramp (350ms in 14 steps): short enough to go unnoticed, long enough
 * to mask the discontinuity.
 *
 * Pure policy with no timer of its own, like [StallCeiling]: the caller silences the
 * player when playback stops, then on starting sets [levelAt] for each step, waiting
 * [stepDelayMs] before each one.
 */
object VolumeRamp {
    const val DURATION_MS: Long = 350L
    const val STEPS: Int = 14

    /** Wait before each step; the last step lands at [DURATION_MS]. */
    val stepDelayMs: Long get() = DURATION_MS / STEPS

    /** Player volume after [step] (1-based) of [STEPS]; full volume at the last step. */
    fun levelAt(step: Int): Float = step.coerceIn(0, STEPS).toFloat() / STEPS
}
