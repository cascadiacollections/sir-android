package com.cascadiacollections.sir

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.cascadiacollections.sir.core.playback.StreamErrorKind
import com.cascadiacollections.sir.core.playback.StreamFailure
import com.cascadiacollections.sir.core.playback.StreamFailureClassifier
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Translates Media3's error vocabulary into `:core:playback`'s [StreamFailure].
 *
 * The mapping lives here rather than in `:core:playback` so that module keeps its property
 * of holding pure policy with no player dependency — the *decision* about what each kind
 * means (and whether it is worth a reconnect attempt) is `StreamFailureClassifier`'s, and is
 * tested without Media3.
 */
@OptIn(UnstableApi::class)
internal fun PlaybackException.toStreamFailure(): StreamFailure =
    StreamFailureClassifier.classify(errorKind(), httpResponseCode())

@OptIn(UnstableApi::class)
internal fun LoadErrorHandlingPolicy.LoadErrorInfo.toStreamFailure(): StreamFailure =
    exception.toStreamFailure()

private fun IOException.toStreamFailure(): StreamFailure =
    StreamFailureClassifier.classify(errorKind(), httpResponseCode())

private fun PlaybackException.errorKind(): StreamErrorKind = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> StreamErrorKind.NO_NETWORK
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> StreamErrorKind.TIMEOUT
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> StreamErrorKind.BAD_HTTP_STATUS
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> StreamErrorKind.NOT_FOUND
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> StreamErrorKind.BAD_CONTENT_TYPE
    // Retrying cannot make an unsupported codec supported, or cleartext permitted.
    // A decoder *init* failure is deliberately absent: a busy hardware decoder is
    // exactly the kind of thing that succeeds on the next attempt.
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    -> StreamErrorKind.UNPLAYABLE

    PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> StreamErrorKind.IO
    else -> StreamErrorKind.PLAYBACK
}

/**
 * The HTTP status behind the failure, when there is one.
 *
 * The exception is looked for along the whole cause chain rather than at `cause` directly:
 * a load error reaches the player wrapped by whatever layer noticed it, and the depth is
 * not part of any contract we should rely on.
 */
@OptIn(UnstableApi::class)
private fun PlaybackException.httpResponseCode(): Int? {
    return cause.httpResponseCode()
}

private fun IOException.errorKind(): StreamErrorKind = when {
    findCause<HttpDataSource.InvalidResponseCodeException>() != null -> StreamErrorKind.BAD_HTTP_STATUS
    findCause<HttpDataSource.InvalidContentTypeException>() != null -> StreamErrorKind.BAD_CONTENT_TYPE
    findCause<FileNotFoundException>() != null -> StreamErrorKind.NOT_FOUND
    findCause<HttpDataSource.CleartextNotPermittedException>() != null -> StreamErrorKind.UNPLAYABLE
    else -> StreamErrorKind.IO
}

private fun Throwable?.httpResponseCode(): Int? =
    findCause<HttpDataSource.InvalidResponseCodeException>()?.responseCode

private inline fun <reified T : Throwable> Throwable?.findCause(): T? {
    var candidate = this
    var depth = 0
    while (candidate != null && depth < MAX_CAUSE_DEPTH) {
        if (candidate is T) return candidate
        candidate = candidate.cause
        depth++
    }
    return null
}

/** Backstop against a self-referencing cause chain; real ones are two or three deep. */
private const val MAX_CAUSE_DEPTH = 8

/** The notification text for a failure the listener needs to be told about. */
@StringRes
internal fun StreamFailure.messageRes(): Int = when (this) {
    StreamFailure.NoNetwork -> R.string.stream_no_connection
    is StreamFailure.StationUnavailable -> R.string.stream_station_unavailable
    StreamFailure.Unplayable -> R.string.stream_unplayable
    StreamFailure.Transient -> R.string.radio_error
}
