package com.cascadiacollections.sir

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.cascadiacollections.sir.core.playback.StreamFailure
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the Media3 half of the failure classification: which `PlaybackException` maps to
 * which [StreamFailure]. The policy itself (what is retryable) is covered without Media3 in
 * `:core:playback`'s `StreamFailureTest`.
 *
 * Robolectric only because building the response-code exception needs a `DataSpec`, which
 * parses a `Uri`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(UnstableApi::class)
class PlaybackFailureMappingTest {

    private fun exception(errorCode: Int, cause: Throwable? = null) =
        PlaybackException("test", cause, errorCode)

    private fun invalidResponseCode(responseCode: Int) = HttpDataSource.InvalidResponseCodeException(
        responseCode,
        /* responseMessage = */ null,
        /* cause = */ null,
        emptyMap(),
        DataSpec.Builder().setUri("https://stream.example/live").build(),
        ByteArray(0),
    )

    @Test
    fun `a connection failure maps to no network`() {
        val failure = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            .toStreamFailure()

        assertEquals(StreamFailure.NoNetwork, failure)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `a timeout stays retryable`() {
        assertTrue(
            exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
                .toStreamFailure()
                .isRetryable
        )
    }

    @Test
    fun `a 404 response code is read off the cause and is not retried`() {
        val failure = exception(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            cause = invalidResponseCode(404),
        ).toStreamFailure()

        assertEquals(StreamFailure.StationUnavailable(404), failure)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `a response code nested deeper in the cause chain is still found`() {
        // How deep the load error is wrapped by the time it reaches the player is not part
        // of any contract, so the whole chain is searched.
        val nested = IOException("wrapped", invalidResponseCode(503))
        val failure = exception(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, cause = nested)
            .toStreamFailure()

        assertEquals(StreamFailure.Transient, failure)
    }

    @Test
    fun `the cause search is bounded`() {
        // Real chains are two or three deep; the bound exists so a pathological one cannot
        // walk forever, and burying the status past it simply means we don't find it.
        val deep = (1..12).fold<Int, Throwable>(invalidResponseCode(404)) { cause, depth ->
            IOException("wrapper $depth", cause)
        }

        assertEquals(
            StreamFailure.Transient,
            exception(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, cause = deep).toStreamFailure()
        )
    }

    @Test
    fun `a missing file maps to an unavailable station`() {
        val failure = exception(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND).toStreamFailure()

        assertEquals(StreamFailure.StationUnavailable(null), failure)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `a non-audio content type is not retried`() {
        assertFalse(
            exception(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE)
                .toStreamFailure()
                .isRetryable
        )
    }

    @Test
    fun `an unsupported codec and blocked cleartext are unplayable`() {
        assertEquals(
            StreamFailure.Unplayable,
            exception(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED).toStreamFailure()
        )
        assertEquals(
            StreamFailure.Unplayable,
            exception(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED).toStreamFailure()
        )
    }

    @Test
    fun `a decoder init failure stays retryable - a busy decoder frees up`() {
        assertTrue(
            exception(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED).toStreamFailure().isRetryable
        )
    }

    @Test
    fun `an unrecognized error code stays retryable`() {
        assertEquals(
            StreamFailure.Transient,
            exception(PlaybackException.ERROR_CODE_UNSPECIFIED).toStreamFailure()
        )
    }

    @Test
    fun `every failure has a message`() {
        listOf(
            StreamFailure.NoNetwork,
            StreamFailure.StationUnavailable(404),
            StreamFailure.Unplayable,
            StreamFailure.Transient,
            StreamFailure.Stalled,
        ).forEach { failure ->
            assertTrue("no message for $failure", failure.messageRes() != 0)
        }
    }

    @Test
    fun `load policy retries transient errors twice with explicit delays`() {
        val policy = StreamLoadErrorHandlingPolicy()

        assertEquals(2_000L, policy.getRetryDelayMsFor(loadError(IOException(), errorCount = 1)))
        assertEquals(4_000L, policy.getRetryDelayMsFor(loadError(IOException(), errorCount = 2)))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(loadError(IOException(), errorCount = 3)))
    }

    @Test
    fun `load policy does not retry unavailable stations`() {
        val policy = StreamLoadErrorHandlingPolicy()

        assertEquals(
            C.TIME_UNSET,
            policy.getRetryDelayMsFor(loadError(invalidResponseCode(404), errorCount = 1))
        )
    }

    private fun loadError(
        exception: IOException,
        errorCount: Int,
    ) = LoadErrorHandlingPolicy.LoadErrorInfo(
        LoadEventInfo(
            LoadEventInfo.getNewId(),
            DataSpec.Builder().setUri("https://stream.example/live").build(),
            Uri.parse("https://stream.example/live"),
            emptyMap(),
            SystemClock.elapsedRealtime(),
            0,
            0,
        ),
        MediaLoadData(C.DATA_TYPE_MEDIA),
        exception,
        errorCount,
    )
}
