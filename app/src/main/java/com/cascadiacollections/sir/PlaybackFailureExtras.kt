package com.cascadiacollections.sir

import android.os.Bundle
import com.cascadiacollections.sir.core.playback.StreamFailure
import com.cascadiacollections.sir.core.playback.StreamFailureCodes

/**
 * How [RadioPlaybackService] hands the typed [StreamFailure] to its controllers.
 *
 * A `PlaybackException` crossing the session boundary keeps its error code but not the
 * HTTP status buried in its cause chain, and a stall give-up is not a player error at all,
 * so the classification the service already made is published as session extras instead
 * (`MediaSession.setSessionExtras`), which every controller receives on connect and on
 * change.
 */
object PlaybackFailureExtras {

    /** [StreamFailureCodes] name of the current failure; absent when playback is healthy. */
    const val KEY_FAILURE = "com.cascadiacollections.sir.extra.STREAM_FAILURE"

    /** Whether the service has a reconnect attempt scheduled or in flight for that failure. */
    const val KEY_RETRYING = "com.cascadiacollections.sir.extra.STREAM_FAILURE_RETRYING"

    fun bundle(failure: StreamFailure?, retrying: Boolean): Bundle = Bundle().apply {
        if (failure != null) {
            putString(KEY_FAILURE, StreamFailureCodes.encode(failure))
            putBoolean(KEY_RETRYING, retrying)
        }
    }

    fun failure(extras: Bundle?): StreamFailure? = StreamFailureCodes.decode(extras?.getString(KEY_FAILURE))

    fun isRetrying(extras: Bundle?): Boolean = failure(extras) != null && extras?.getBoolean(KEY_RETRYING) == true
}
