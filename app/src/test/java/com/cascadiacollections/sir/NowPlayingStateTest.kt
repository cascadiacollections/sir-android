package com.cascadiacollections.sir

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.StreamFailure
import java.util.Locale
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
            assertThat(state.failure).isEqualTo(failure)
            assertThat(state.displayedFailure).isEqualTo(failure)
            assertThat(state.isReconnecting).isFalse()
            assertThat(state.status).isEqualTo(PlaybackStatus.FAILED)
            assertThat(state.transportAction).isEqualTo(TransportAction.RETRY)
        }
    }

    @Test
    fun `a failure being retried shows reconnecting and offers cancel`() {
        val state = connected.withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.NoNetwork, retrying = true))
        assertThat(state.isReconnecting).isTrue()
        assertThat(state.status).isEqualTo(PlaybackStatus.RECONNECTING)
        assertThat(state.transportAction).isEqualTo(TransportAction.CANCEL)
    }

    @Test
    fun `cleared extras clear both the typed failure and the untyped error`() {
        val failed = connected.copy(isError = true)
            .withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.Transient, retrying = false))
        val cleared = failed.withSessionExtras(Bundle())
        assertThat(cleared.failure).isNull()
        assertThat(cleared.isError).isFalse()
        assertThat(cleared.isReconnecting).isFalse()
        assertThat(cleared.status).isEqualTo(PlaybackStatus.IDLE)
    }

    @Test
    fun `retrying without a failure is not reconnecting`() {
        val extras = Bundle().apply { putBoolean(PlaybackFailureExtras.KEY_RETRYING, true) }
        assertThat(connected.withSessionExtras(extras).isReconnecting).isFalse()
    }

    @Test
    fun `an untyped player error is shown as the generic stream error`() {
        val state = connected.copy(isError = true)
        assertThat(state.displayedFailure).isEqualTo(StreamFailure.Transient)
        assertThat(state.status).isEqualTo(PlaybackStatus.FAILED)
        assertThat(state.copy(isBuffering = true).status).isEqualTo(PlaybackStatus.RECONNECTING)
    }

    // ---- Status and transport action without a failure ----

    @Test
    fun `status and action follow the transport`() {
        assertThat(RadioUiState().status).isEqualTo(PlaybackStatus.CONNECTING)
        assertThat(RadioUiState().transportAction).isEqualTo(TransportAction.PLAY)

        val buffering = connected.copy(isBuffering = true)
        assertThat(buffering.status).isEqualTo(PlaybackStatus.CONNECTING)
        assertThat(buffering.transportAction).isEqualTo(TransportAction.CANCEL)

        val playing = connected.copy(isPlaying = true)
        assertThat(playing.status).isEqualTo(PlaybackStatus.LIVE)
        assertThat(playing.transportAction).isEqualTo(TransportAction.PAUSE)

        assertThat(connected.status).isEqualTo(PlaybackStatus.IDLE)
        assertThat(connected.transportAction).isEqualTo(TransportAction.PLAY)
    }

    // ---- A paused stream nobody asked to play (offline cold-start regression) ----

    @Test
    fun `a paused prepare that is buffering shows play, not connecting`() {
        val player = PlayerTestHelper.createMockPlayer(playWhenReady = false, playbackState = Player.STATE_BUFFERING)

        val state = connected.withPlayer(player)

        assertThat(state.isBuffering).isFalse()
        assertThat(state.isPlayRequested).isFalse()
        assertThat(state.status).isEqualTo(PlaybackStatus.IDLE)
        assertThat(state.transportAction).isEqualTo(TransportAction.PLAY)
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

        assertThat(state.isError).isFalse()
        assertThat(state.displayedFailure).isNull()
        assertThat(state.status).isEqualTo(PlaybackStatus.IDLE)
        assertThat(state.transportAction).isEqualTo(TransportAction.PLAY)
    }

    @Test
    fun `the failure surfaces once the listener asks to play`() {
        val requested = PlayerTestHelper.createMockPlayer(playWhenReady = true, playbackState = Player.STATE_BUFFERING)

        val reconnecting = connected.withPlayer(requested)
            .withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.NoNetwork, retrying = true))
        assertThat(reconnecting.status).isEqualTo(PlaybackStatus.RECONNECTING)
        assertThat(reconnecting.transportAction).isEqualTo(TransportAction.CANCEL)

        val failed = reconnecting
            .withPlayer(PlayerTestHelper.createMockPlayer(playWhenReady = true, playbackState = Player.STATE_IDLE))
            .withPlayerError(playWhenReady = true)
            .withSessionExtras(PlaybackFailureExtras.bundle(StreamFailure.NoNetwork, retrying = false))
        assertThat(failed.displayedFailure).isEqualTo(StreamFailure.NoNetwork)
        assertThat(failed.status).isEqualTo(PlaybackStatus.FAILED)
        assertThat(failed.transportAction).isEqualTo(TransportAction.RETRY)
    }

    @Test
    fun `pausing a failed stream drops the untyped error`() {
        val failed = connected.withPlayerError(playWhenReady = true)
        assertThat(failed.isError).isTrue()

        val paused = failed.withPlayer(PlayerTestHelper.createMockPlayer(playWhenReady = false))

        assertThat(paused.isError).isFalse()
        assertThat(paused.status).isEqualTo(PlaybackStatus.IDLE)
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

        assertThat(playing.isError).isFalse()
        assertThat(playing.status).isEqualTo(PlaybackStatus.LIVE)
    }

    // ---- Metadata → UiState ----

    @Test
    fun `unresolved metadata is not a track`() {
        val metadata = MediaMetadata.Builder()
            .setTitle("SIR")
            .setArtist("Live stream")
            .build()
        val state = connected.withMediaMetadata(metadata)
        assertThat(state.trackTitle).isNull()
        assertThat(state.artist).isNull()
        assertThat(state.albumArtUrl).isNull()
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
        assertThat(state.trackTitle).isEqualTo("Around the World")
        assertThat(state.artist).isEqualTo("Daft Punk")
        assertThat(state.albumArtUrl).isEqualTo("https://a/600x600bb.jpg")
        assertThat(state.trackViewUrl).isEqualTo("https://music.apple.com/t")
    }

    // ---- Track line ----

    @Test
    fun `track line prefers title and artist, then genre`() {
        val station =
            Station(id = "s", name = "Station", url = "https://s", tags = "jazz, smooth jazz")
        assertThat(connected.copy(trackTitle = "Title", artist = "Artist").trackLine()).isEqualTo("Title — Artist")
        assertThat(connected.copy(trackTitle = "Title", artist = " ").trackLine()).isEqualTo("Title")
        assertThat(connected.copy(station = station).trackLine(Locale.US)).isEqualTo("Jazz")
        assertThat(connected.trackLine()).isNull()
        assertThat(connected.copy(station = station.copy(tags = "")).trackLine()).isNull()
    }

    @Test
    fun `only stations with an id can be favourited`() {
        assertThat(connected.canFavorite).isFalse()
        assertThat(connected.copy(station = Station(name = "Imported", url = "https://i")).canFavorite).isFalse()
        assertThat(connected.copy(station = Station(id = "x", name = "X", url = "https://x")).canFavorite).isTrue()
    }
}
