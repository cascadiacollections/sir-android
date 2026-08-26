package com.cascadiacollections.sir.core.playback

/**
 * The shape of a player error, reduced to what the retry policy needs to know.
 *
 * Deliberately not Media3's `PlaybackException`: this module holds pure policy with no
 * player dependency, so the handful of error codes are mapped to these kinds at the call
 * site in `:app` (`toStreamFailure`) and the decision about what each one *means* is tested
 * here without a player.
 */
enum class StreamErrorKind {
    /** The connection could not be established at all — offline, or the host is unreachable. */
    NO_NETWORK,

    /** The connection was established but stalled long enough to time out. */
    TIMEOUT,

    /** The server answered with an error status; [StreamFailureClassifier] reads the code. */
    BAD_HTTP_STATUS,

    /** The endpoint is gone (404 and friends). */
    NOT_FOUND,

    /** The server sent something that isn't audio — an HTML error page, or a playlist. */
    BAD_CONTENT_TYPE,

    /** The stream is well-formed but this device cannot play it: unsupported codec, cleartext blocked. */
    UNPLAYABLE,

    /** An IO failure with no more specific classification. */
    IO,

    /** A decoder, renderer or other in-player failure. */
    PLAYBACK,
}

/**
 * Why a stream stopped, and whether retrying it can plausibly help.
 *
 * Mirrors ShoutKit's `PlaybackError` so both clients spend their bounded reconnect budget
 * on the same failures. Previously every `PlaybackException` was retried identically, so a
 * station answering 404 held a wake lock through the full backoff — about a minute — before
 * showing the same generic error it could have shown immediately.
 */
sealed interface StreamFailure {
    /** Whether the bounded auto-reconnect should spend an attempt on this. */
    val isRetryable: Boolean

    /** The stream could not be reached. Usually the listener's connection, and transient. */
    data object NoNetwork : StreamFailure {
        override val isRetryable: Boolean = true
    }

    /**
     * The station's endpoint answered, but not with a stream it will serve. A property of
     * the request rather than of the moment, so retrying repeats it verbatim.
     */
    data class StationUnavailable(val responseCode: Int? = null) : StreamFailure {
        override val isRetryable: Boolean = false
    }

    /** This device cannot play what the station is sending. No amount of retrying changes that. */
    data object Unplayable : StreamFailure {
        override val isRetryable: Boolean = false
    }

    /** Anything that plausibly recovers on its own: a timeout, a 5xx, a mid-stream drop. */
    data object Transient : StreamFailure {
        override val isRetryable: Boolean = true
    }

    /**
     * The stream sat in `STATE_BUFFERING` past [StallCeiling]'s timeout and the bounded
     * reconnect budget it was handed there is already spent. Not retryable itself — the
     * ceiling already spent its one reconnect attempt before reporting this — but not a
     * station-side error either, so the caller parks playback as paused rather than failed.
     */
    data object Stalled : StreamFailure {
        override val isRetryable: Boolean = false
    }
}

/** Maps a [StreamErrorKind] (plus an HTTP status where there is one) to a [StreamFailure]. */
object StreamFailureClassifier {

    fun classify(kind: StreamErrorKind, responseCode: Int? = null): StreamFailure = when (kind) {
        StreamErrorKind.NO_NETWORK -> StreamFailure.NoNetwork
        StreamErrorKind.NOT_FOUND -> StreamFailure.StationUnavailable(responseCode)
        StreamErrorKind.BAD_CONTENT_TYPE -> StreamFailure.StationUnavailable(responseCode)
        StreamErrorKind.UNPLAYABLE -> StreamFailure.Unplayable
        StreamErrorKind.BAD_HTTP_STATUS ->
            if (responseCode != null && isPermanent(responseCode)) {
                StreamFailure.StationUnavailable(responseCode)
            } else {
                StreamFailure.Transient
            }

        StreamErrorKind.TIMEOUT,
        StreamErrorKind.IO,
        StreamErrorKind.PLAYBACK,
        -> StreamFailure.Transient
    }

    /**
     * A 4xx describes the request: the mount was renamed, removed, or needs credentials, and
     * it will answer identically on the next attempt. 408 and 429 are the exceptions — both
     * explicitly invite a later retry — and a 5xx is usually an Icecast server restarting,
     * which is exactly what the backoff exists for.
     */
    private fun isPermanent(responseCode: Int): Boolean =
        responseCode in 400..499 && responseCode != HTTP_REQUEST_TIMEOUT && responseCode != HTTP_TOO_MANY_REQUESTS

    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
}
