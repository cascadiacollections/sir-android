package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class StreamFailureTest {

    @Test
    fun `a failed connection is retryable`() {
        val failure = StreamFailureClassifier.classify(StreamErrorKind.NO_NETWORK)

        assertThat(failure).isEqualTo(StreamFailure.NoNetwork)
        assertThat(failure.isRetryable).isTrue()
    }

    @Test
    fun `a timeout is retryable`() {
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.TIMEOUT).isRetryable).isTrue()
    }

    @Test
    fun `a 404 is not retried`() {
        val failure = StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 404)

        assertThat(failure).isEqualTo(StreamFailure.StationUnavailable(404))
        assertThat(failure.isRetryable).isFalse()
    }

    @Test
    fun `a 401 is not retried either`() {
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 401).isRetryable)
            .isFalse()
    }

    @Test
    fun `408 and 429 are retried despite being 4xx`() {
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 408).isRetryable)
            .isTrue()
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 429).isRetryable)
            .isTrue()
    }

    @Test
    fun `a 5xx is retried - an Icecast server restarting is the common case`() {
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 503).isRetryable)
            .isTrue()
    }

    @Test
    fun `a bad status with no code available is treated as transient`() {
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = null))
            .isEqualTo(StreamFailure.Transient)
    }

    @Test
    fun `a missing endpoint is not retried and carries its code`() {
        val failure = StreamFailureClassifier.classify(StreamErrorKind.NOT_FOUND, responseCode = 410)

        assertThat(failure).isEqualTo(StreamFailure.StationUnavailable(410))
        assertThat(failure.isRetryable).isFalse()
    }

    @Test
    fun `a non-audio response is not retried`() {
        // An HTML error page or a playlist where audio was expected is a property of the
        // endpoint, not of the moment.
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.BAD_CONTENT_TYPE).isRetryable).isFalse()
    }

    @Test
    fun `an unplayable stream is not retried`() {
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.UNPLAYABLE)).isEqualTo(StreamFailure.Unplayable)
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.UNPLAYABLE).isRetryable).isFalse()
    }

    @Test
    fun `unclassified IO and in-player failures stay retryable`() {
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.IO).isRetryable).isTrue()
        assertThat(StreamFailureClassifier.classify(StreamErrorKind.PLAYBACK).isRetryable).isTrue()
    }
}
