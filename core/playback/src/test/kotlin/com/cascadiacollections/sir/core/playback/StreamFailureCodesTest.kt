package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class StreamFailureCodesTest {

    @Test
    fun `every failure round trips`() {
        listOf(
            StreamFailure.NoNetwork,
            StreamFailure.StationUnavailable(),
            StreamFailure.Unplayable,
            StreamFailure.Transient,
            StreamFailure.Stalled
        ).forEach { failure ->
            assertThat(StreamFailureCodes.decode(StreamFailureCodes.encode(failure))).isEqualTo(failure)
        }
    }

    @Test
    fun `the response code is not part of the wire form`() {
        val decoded = StreamFailureCodes.decode(StreamFailureCodes.encode(StreamFailure.StationUnavailable(404)))
        assertThat(decoded).isEqualTo(StreamFailure.StationUnavailable())
    }

    @Test
    fun `unknown and missing codes decode to no failure`() {
        assertThat(StreamFailureCodes.decode(null)).isNull()
        assertThat(StreamFailureCodes.decode("from_a_newer_build")).isNull()
    }
}
