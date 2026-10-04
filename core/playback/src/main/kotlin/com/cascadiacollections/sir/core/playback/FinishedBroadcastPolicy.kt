package com.cascadiacollections.sir.core.playback

/** What to do when the player reaches the end of what it was playing. */
enum class EndOfStreamAction {
    /** A live stream has no end: the server dropped us. Rejoin through the reconnect budget. */
    REJOIN,

    /** A finished broadcast with "Loop finished broadcasts" on: play it again from the start. */
    LOOP,

    /** A finished broadcast: stop at the start, paused, so play replays it. */
    STOP
}

/**
 * Tells a broadcast that genuinely finished (an hourly newscast served as a file) from a live
 * stream whose connection dropped, which reaches the same `STATE_ENDED`.
 *
 * Previously every end was treated as a live drop, so a finite file was "rejoined" — replayed
 * from the top — up to the whole reconnect budget and then reported as a failure. ShoutKit's
 * Playback → "Loop finished broadcasts" (off by default) chooses between stopping and looping.
 */
object FinishedBroadcastPolicy {

    /**
     * Whether the current item is finite: not live, not dynamic, and of known duration. Any
     * doubt counts as live — rejoining a finite file is a smaller mistake than giving up on a
     * live stream that merely dropped.
     */
    fun isFinite(isLive: Boolean, isDynamic: Boolean, durationMs: Long?): Boolean =
        !isLive && !isDynamic && durationMs != null && durationMs > 0

    fun onEnded(isFinite: Boolean, loopFinishedBroadcasts: Boolean): EndOfStreamAction = when {
        !isFinite -> EndOfStreamAction.REJOIN
        loopFinishedBroadcasts -> EndOfStreamAction.LOOP
        else -> EndOfStreamAction.STOP
    }

    /**
     * Whether the player should repeat the current item itself (`REPEAT_MODE_ONE`), so a
     * looping broadcast never reaches `STATE_ENDED` at all. Never for a live stream: a live
     * end is a drop, and repeating it would hide that from the reconnect budget.
     */
    fun repeatsCurrentItem(isFinite: Boolean, loopFinishedBroadcasts: Boolean): Boolean =
        isFinite && loopFinishedBroadcasts
}
