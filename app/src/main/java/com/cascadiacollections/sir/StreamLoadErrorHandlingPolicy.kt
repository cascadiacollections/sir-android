package com.cascadiacollections.sir

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Retries only load failures that the stream policy considers transient.
 *
 * Two prepares, each with the initial load plus [MAX_LOAD_RETRIES] retries, bound an offline
 * station to six load attempts and fourteen seconds of scheduled backoff before surfacing an
 * error.
 */
@OptIn(UnstableApi::class)
internal class StreamLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy(MAX_LOAD_RETRIES) {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (
            loadErrorInfo.exception.toStreamFailure().isRetryable &&
            loadErrorInfo.errorCount <= MAX_LOAD_RETRIES
        ) {
            loadErrorInfo.errorCount * RETRY_DELAY_MS
        } else {
            C.TIME_UNSET
        }

    companion object {
        const val MAX_LOAD_RETRIES = 2
        const val MAX_LOAD_ATTEMPTS = 6
        const val MAX_PREPARE_ATTEMPTS = MAX_LOAD_ATTEMPTS / (MAX_LOAD_RETRIES + 1)
        private const val RETRY_DELAY_MS = 2_000L
    }
}
