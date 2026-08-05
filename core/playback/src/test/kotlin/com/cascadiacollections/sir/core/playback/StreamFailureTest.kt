package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFailureTest {

    @Test
    fun `a failed connection is retryable`() {
        val failure = StreamFailureClassifier.classify(StreamErrorKind.NO_NETWORK)

        assertEquals(StreamFailure.NoNetwork, failure)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `a timeout is retryable`() {
        assertTrue(StreamFailureClassifier.classify(StreamErrorKind.TIMEOUT).isRetryable)
    }

    @Test
    fun `a 404 is not retried`() {
        val failure = StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 404)

        assertEquals(StreamFailure.StationUnavailable(404), failure)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `a 401 is not retried either`() {
        assertFalse(
            StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 401).isRetryable
        )
    }

    @Test
    fun `408 and 429 are retried despite being 4xx`() {
        assertTrue(
            StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 408).isRetryable
        )
        assertTrue(
            StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 429).isRetryable
        )
    }

    @Test
    fun `a 5xx is retried - an Icecast server restarting is the common case`() {
        assertTrue(
            StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = 503).isRetryable
        )
    }

    @Test
    fun `a bad status with no code available is treated as transient`() {
        assertEquals(
            StreamFailure.Transient,
            StreamFailureClassifier.classify(StreamErrorKind.BAD_HTTP_STATUS, responseCode = null)
        )
    }

    @Test
    fun `a missing endpoint is not retried and carries its code`() {
        val failure = StreamFailureClassifier.classify(StreamErrorKind.NOT_FOUND, responseCode = 410)

        assertEquals(StreamFailure.StationUnavailable(410), failure)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `a non-audio response is not retried`() {
        // An HTML error page or a playlist where audio was expected is a property of the
        // endpoint, not of the moment.
        assertFalse(StreamFailureClassifier.classify(StreamErrorKind.BAD_CONTENT_TYPE).isRetryable)
    }

    @Test
    fun `an unplayable stream is not retried`() {
        assertEquals(
            StreamFailure.Unplayable,
            StreamFailureClassifier.classify(StreamErrorKind.UNPLAYABLE)
        )
        assertFalse(StreamFailureClassifier.classify(StreamErrorKind.UNPLAYABLE).isRetryable)
    }

    @Test
    fun `unclassified IO and in-player failures stay retryable`() {
        assertTrue(StreamFailureClassifier.classify(StreamErrorKind.IO).isRetryable)
        assertTrue(StreamFailureClassifier.classify(StreamErrorKind.PLAYBACK).isRetryable)
    }
}
