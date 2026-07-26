package com.cascadiacollections.sir.core.playback

/**
 * Decides how playback reacts to the audio output route changing.
 *
 * Android delivers `ACTION_AUDIO_BECOMING_NOISY` when headphones are unplugged or a
 * Bluetooth device disconnects; pausing is mandatory so audio never blasts out of the
 * speaker. Resuming when the route comes back is a courtesy, and must only happen when
 * *we* were the ones who paused — resuming a stream the user deliberately paused is a
 * bug users notice immediately.
 */
class AudioRoutePolicy {

    /** True when playback is currently suspended because the output route went away. */
    var pausedByRouteLoss: Boolean = false
        private set

    /**
     * Call when the route becomes noisy. Returns true if the caller should pause.
     */
    fun onBecomingNoisy(isPlaying: Boolean): Boolean {
        if (!isPlaying) return false
        pausedByRouteLoss = true
        return true
    }

    /**
     * Call when an output route becomes available again. Returns true if the caller
     * should resume.
     */
    fun onRouteRestored(): Boolean {
        if (!pausedByRouteLoss) return false
        pausedByRouteLoss = false
        return true
    }

    /**
     * Call whenever playback state changes for any other reason (user pressed play or
     * pause, the stream ended). This clears the claim on the pause so a later route
     * change cannot resume something the user stopped on purpose.
     */
    fun onPlaybackStateChangedByUser() {
        pausedByRouteLoss = false
    }
}
