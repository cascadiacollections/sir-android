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
     * Call when playback starts, whatever caused it — the notification action, the in-app
     * transport, Android Auto, Wear, or a Bluetooth AVRCP command.
     *
     * Once audio is running again the pause we claimed is over, so any *later* pause
     * belongs to the user and a reconnect must not undo it. Releasing the claim here
     * rather than on the pause transition is deliberate: the route-loss pause itself
     * arrives as a pause, so a pause-side hook would immediately cancel the claim it is
     * meant to protect.
     */
    fun onPlaybackStarted() {
        pausedByRouteLoss = false
    }

    /**
     * Call when the user stops playback outright. Stopping never produces a start event,
     * so the claim has to be released explicitly or a later reconnect would resume a
     * stream the user deliberately ended.
     */
    fun onPlaybackStateChangedByUser() {
        pausedByRouteLoss = false
    }
}
