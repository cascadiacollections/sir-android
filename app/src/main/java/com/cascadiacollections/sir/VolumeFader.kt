package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.playback.VolumeRamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs [VolumeRamp] against a player's volume for [RadioPlaybackService]: silent while not
 * playing, then a fade up on each start. Separate from the service so the timing is
 * testable on a virtual clock.
 *
 * [setVolume] is called on [scope]'s dispatcher (the main thread, for ExoPlayer).
 */
internal class VolumeFader(
    private val scope: CoroutineScope,
    private val setVolume: (Float) -> Unit,
) {
    private var job: Job? = null

    /** Playback stopped: zero the volume so the next start begins silent. */
    fun silence() {
        job?.cancel()
        setVolume(0f)
    }

    /** Playback started: ramp up from silence, replacing any ramp in progress. */
    fun fadeIn() {
        job?.cancel()
        job = scope.launch {
            for (step in 1..VolumeRamp.STEPS) {
                delay(VolumeRamp.stepDelayMs)
                setVolume(VolumeRamp.levelAt(step))
            }
        }
    }
}
