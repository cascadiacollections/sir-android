package com.cascadiacollections.sir

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.StreamFailure
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How the session's failure extras and metadata become [RadioUiState], and how that state
 * drives the status badge, the transport action and the track line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingStateTest {

    private val connected = RadioUiState(isConnected = true)

    // ---- Failure extras → UiState ----

    @Test
    fun `every typed failure reaches the UI and offers retry`() {
        listOf(
            StreamFailure.NoNetwork,
            StreamFailure.StationUnavailable(),
            StreamFailure.Unplayable,
            StreamFailure.Transient,
            StreamFailure.Stalled
        ).forEach { failure ->
            val state = connected.withSessionExtras(PlaybackFailureExtras.bundle(failure, retrying = false))
            assertEquals(failure, state.failure)
            assertEquals(failure, state.displayedFailure)
            assertFalse(state.isReconnecting)
            assertEquals(PlaybackStatus.FAILED, state.status)
            assertEquals(TransportAction.RETRY, state.transportAction)
        }
    }

    @Test
    fun `a failure being retried shows reconnecting and offers cancel`() {
        val state = connected.withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.NoNetwork, retrying = true))
        assertTrue(state.isReconnecting)
        assertEquals(PlaybackStatus.RECONNECTING, state.status)
        assertEquals(TransportAction.CANCEL, state.transportAction)
    }

    @Test
    fun `cleared extras clear both the typed failure and the untyped error`() {
        val failed = connected.copy(isError = true)
            .withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.Transient, retrying = false))
        val cleared = failed.withSessionExtras(Bundle())
        assertNull(cleared.failure)
        assertFalse(cleared.isError)
        assertFalse(cleared.isReconnecting)
        assertEquals(PlaybackStatus.IDLE, cleared.status)
    }

    @Test
    fun `retrying without a failure is not reconnecting`() {
        val extras = Bundle().apply { putBoolean(PlaybackFailureExtras.KEY_RETRYING, true) }
        assertFalse(connected.withSessionExtras(extras).isReconnecting)
    }

    @Test
    fun `an untyped player error is shown as the generic stream error`() {
        val state = connected.copy(isError = true)
        assertEquals(StreamFailure.Transient, state.displayedFailure)
        assertEquals(PlaybackStatus.FAILED, state.status)
        assertEquals(PlaybackStatus.RECONNECTING, state.copy(isBuffering = true).status)
    }

    // ---- Status and transport action without a failure ----

    @Test
    fun `status and action follow the transport`() {
        assertEquals(PlaybackStatus.CONNECTING, RadioUiState().status)
        assertEquals(TransportAction.PLAY, RadioUiState().transportAction)

        val buffering = connected.copy(isBuffering = true)
        assertEquals(PlaybackStatus.CONNECTING, buffering.status)
        assertEquals(TransportAction.CANCEL, buffering.transportAction)

        val playing = connected.copy(isPlaying = true)
        assertEquals(PlaybackStatus.LIVE, playing.status)
        assertEquals(TransportAction.PAUSE, playing.transportAction)

        assertEquals(PlaybackStatus.IDLE, connected.status)
        assertEquals(TransportAction.PLAY, connected.transportAction)
    }

    // ---- A paused stream nobody asked to play (offline cold-start regression) ----

    @Test
    fun `a paused prepare that is buffering shows play, not connecting`() {
        val player = PlayerTestHelper.createMockPlayer(playWhenReady = false, playbackState = Player.STATE_BUFFERING)

        val state = connected.withPlayer(player)

        assertFalse(state.isBuffering)
        assertFalse(state.isPlayRequested)
        assertEquals(PlaybackStatus.IDLE, state.status)
        assertEquals(TransportAction.PLAY, state.transportAction)
    }

    @Test
    fun `offline cold launch with the last station paused shows play, not reconnecting`() {
        // Observed on device: airplane mode, cold launch, last station paused. The service's
        // paused prepare failed and published a retrying failure, so the Listen screen and
        // mini player said "Reconnecting…" with a Cancel button while the session was PAUSED.
        val paused = PlayerTestHelper.createMockPlayer(playWhenReady = false, playbackState = Player.STATE_IDLE)

        val state = connected
            .withPlayer(paused)
            .withPlayerError(playWhenReady = false)
            .withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.NoNetwork, retrying = true))

        assertFalse(state.isError)
        assertNull(state.displayedFailure)
        assertEquals(PlaybackStatus.IDLE, state.status)
        assertEquals(TransportAction.PLAY, state.transportAction)
    }

    @Test
    fun `the failure surfaces once the listener asks to play`() {
        val requested = PlayerTestHelper.createMockPlayer(playWhenReady = true, playbackState = Player.STATE_BUFFERING)

        val reconnecting = connected.withPlayer(requested)
            .withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.NoNetwork, retrying = true))
        assertEquals(PlaybackStatus.RECONNECTING, reconnecting.status)
        assertEquals(TransportAction.CANCEL, reconnecting.transportAction)

        val failed = reconnecting
            .withPlayer(PlayerTestHelper.createMockPlayer(playWhenReady = true, playbackState = Player.STATE_IDLE))
            .withPlayerError(playWhenReady = true)
            .withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.NoNetwork, retrying = false))
        assertEquals(StreamFailure.NoNetwork, failed.displayedFailure)
        assertEquals(PlaybackStatus.FAILED, failed.status)
        assertEquals(TransportAction.RETRY, failed.transportAction)
    }

    @Test
    fun `pausing a failed stream drops the untyped error`() {
        val failed = connected.withPlayerError(playWhenReady = true)
        assertTrue(failed.isError)

        val paused = failed.withPlayer(PlayerTestHelper.createMockPlayer(playWhenReady = false))

        assertFalse(paused.isError)
        assertEquals(PlaybackStatus.IDLE, paused.status)
    }

    @Test
    fun `a playing stream reads as live and its error clears once ready`() {
        val playing = connected.withPlayerError(playWhenReady = true)
            .withPlayer(
                PlayerTestHelper.createMockPlayer(
                    isPlaying = true,
                    playWhenReady = true,
                    playbackState = Player.STATE_READY
                )
            )

        assertFalse(playing.isError)
        assertEquals(PlaybackStatus.LIVE, playing.status)
    }

    // ---- Metadata → UiState ----

    @Test
    fun `unresolved metadata is not a track`() {
        val metadata = MediaMetadata.Builder()
            .setTitle("SIR")
            .setArtist("Live stream")
            .build()
        val state = connected.withMediaMetadata(metadata)
        assertNull(state.trackTitle)
        assertNull(state.artist)
        assertNull(state.albumArtUrl)
    }

    @Test
    fun `resolved metadata carries the track and its album art`() {
        val metadata = MediaMetadata.Builder()
            .setTitle("Around the World")
            .setArtist("Daft Punk")
            .setExtras(
                Bundle().apply {
                    putBoolean(RadioPlaybackService.EXTRA_HAS_RESOLVED_TRACK, true)
                    putString(RadioPlaybackService.EXTRA_ALBUM_ART_URL, "https://a/600x600bb.jpg")
                    putString(RadioPlaybackService.EXTRA_TRACK_VIEW_URL, "https://music.apple.com/t")
                }
            )
            .build()
        val state = connected.withMediaMetadata(metadata)
        assertEquals("Around the World", state.trackTitle)
        assertEquals("Daft Punk", state.artist)
        assertEquals("https://a/600x600bb.jpg", state.albumArtUrl)
        assertEquals("https://music.apple.com/t", state.trackViewUrl)
    }

    // ---- Track line ----

    @Test
    fun `track line prefers title and artist, then genre`() {
        val station =
            Station(id = "s", name = "Station", url = "https://s", tags = "jazz, smooth jazz")
        assertEquals("Title — Artist", connected.copy(trackTitle = "Title", artist = "Artist").trackLine())
        assertEquals("Title", connected.copy(trackTitle = "Title", artist = " ").trackLine())
        assertEquals("Jazz", connected.copy(station = station).trackLine(Locale.US))
        assertNull(connected.trackLine())
        assertNull(connected.copy(station = station.copy(tags = "")).trackLine())
    }

    @Test
    fun `only stations with an id can be favourited`() {
        assertFalse(connected.canFavorite)
        assertFalse(connected.copy(station = Station(name = "Imported", url = "https://i")).canFavorite)
        assertTrue(connected.copy(station = Station(id = "x", name = "X", url = "https://x")).canFavorite)
    }
}
