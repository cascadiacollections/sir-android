package com.cascadiacollections.sir

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.cascadiacollections.sir.core.playback.ReconnectBudget

/**
 * Surfaces every load failure to the player at once instead of retrying it inside Media3.
 *
 * Recovery has exactly one owner: `RadioPlaybackService`'s `StreamRecovery`, which spends
 * ShoutKit's budget of three reconnects (2 s, 4 s, 8 s) on failures that can plausibly
 * recover and shows "Reconnecting…" while it does. Media3's default policy retried loads
 * underneath that, so the two layers multiplied — and the inner retries were invisible to
 * the UI. See [ReconnectBudget] and B4 in `docs/audioplayer-dependency-synergies.md`.
 */
@OptIn(UnstableApi::class)
internal class StreamLoadErrorHandlingPolicy :
    DefaultLoadErrorHandlingPolicy(ReconnectBudget.LOAD_RETRIES_PER_CONNECTION) {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        C.TIME_UNSET
}
