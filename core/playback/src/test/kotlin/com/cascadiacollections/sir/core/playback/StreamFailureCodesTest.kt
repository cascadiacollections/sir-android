package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamFailureCodesTest {

    @Test
    fun `every failure round trips`() {
        listOf(
            StreamFailure.NoNetwork,
            StreamFailure.StationUnavailable(),
            StreamFailure.Unplayable,
            StreamFailure.Transient,
            StreamFailure.Stalled,
        ).forEach { failure ->
            assertEquals(failure, StreamFailureCodes.decode(StreamFailureCodes.encode(failure)))
        }
    }

    @Test
    fun `the response code is not part of the wire form`() {
        val decoded = StreamFailureCodes.decode(StreamFailureCodes.encode(StreamFailure.StationUnavailable(404)))
        assertEquals(StreamFailure.StationUnavailable(), decoded)
    }

    @Test
    fun `unknown and missing codes decode to no failure`() {
        assertNull(StreamFailureCodes.decode(null))
        assertNull(StreamFailureCodes.decode("from_a_newer_build"))
    }
}
