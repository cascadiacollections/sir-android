package com.cascadiacollections.sir

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.StreamFailure
import java.util.Locale

/**
 * What the Now Playing status badge says. Mirrors ShoutKit's transport states: a stream
 * is connecting, reconnecting after a failure, live, failed, or simply not playing.
 */
enum class PlaybackStatus { CONNECTING, RECONNECTING, LIVE, FAILED, IDLE }

/** What the big play button does when tapped, which is also what it announces. */
enum class TransportAction { PLAY, PAUSE, CANCEL, RETRY }

/**
 * The failure to show. The typed one comes from the service's session extras; a bare player
 * error with no classification (an older service, or the extras not having arrived yet) is
 * shown as the generic stream error rather than not at all.
 */
val RadioUiState.displayedFailure: StreamFailure?
    get() = (failure ?: StreamFailure.Transient.takeIf { isError })?.takeIf { isPlayRequested }

/**
 * A paused stream the listener hasn't asked to play is idle whatever the player is doing
 * underneath — a cold start prepares the last station paused, and offline that prepare
 * buffers and then fails. Neither is the listener's business until they press play.
 */
val RadioUiState.status: PlaybackStatus
    get() {
        val failed = displayedFailure != null
        val buffering = isBuffering && isPlayRequested
        return when {
            !isConnected -> PlaybackStatus.CONNECTING
            failed && (isReconnecting || buffering) -> PlaybackStatus.RECONNECTING
            failed -> PlaybackStatus.FAILED
            buffering -> PlaybackStatus.CONNECTING
            isPlaying -> PlaybackStatus.LIVE
            else -> PlaybackStatus.IDLE
        }
    }

/**
 * Play, Pause, Cancel while a connection is being attempted, Retry once it has failed.
 * Before the controller connects the button is a Play, which starts the service.
 */
val RadioUiState.transportAction: TransportAction
    get() = when (status) {
        PlaybackStatus.CONNECTING -> if (isConnected) TransportAction.CANCEL else TransportAction.PLAY
        PlaybackStatus.RECONNECTING -> TransportAction.CANCEL
        PlaybackStatus.FAILED -> TransportAction.RETRY
        PlaybackStatus.LIVE -> TransportAction.PAUSE
        PlaybackStatus.IDLE -> TransportAction.PLAY
    }

/** Whether the heart can act: only a directory or saved station has an id to save under. */
val RadioUiState.canFavorite: Boolean
    get() = station?.id?.isNotBlank() == true

/**
 * The line under the station name: "Title — Artist" from ICY metadata, else the station's
 * first genre tag, else null (the UI then says "Live radio").
 */
fun RadioUiState.trackLine(locale: Locale = Locale.getDefault()): String? {
    val title = trackTitle?.trim()?.takeIf { it.isNotEmpty() }
    val by = artist?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        title != null && by != null -> "$title — $by"
        title != null -> title
        else -> station?.genreLabel(locale)
    }
}

/** The station's first tag, capitalised — "jazz,smooth jazz" reads as "Jazz". */
fun Station.genreLabel(locale: Locale = Locale.getDefault()): String? =
    tagList.firstOrNull()?.replaceFirstChar { it.titlecase(locale) }

/**
 * Applies the player's transport state. Buffering only counts while playback is requested,
 * so the spinner and "Connecting…" never appear for a paused prepare; likewise a player
 * error is dropped once nobody wants audio.
 */
internal fun RadioUiState.withPlayer(player: Player): RadioUiState = copy(
    isPlaying = player.isActuallyPlaying,
    isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
    isError = isError && player.playWhenReady && player.playbackState != Player.STATE_READY,
    isPlayRequested = player.playWhenReady,
)

/** A player error, surfaced only if the listener had asked to play. */
internal fun RadioUiState.withPlayerError(playWhenReady: Boolean): RadioUiState =
    copy(isError = playWhenReady)

/** Applies the service's failure extras (see [PlaybackFailureExtras]). */
internal fun RadioUiState.withSessionExtras(extras: Bundle?): RadioUiState {
    val failure = PlaybackFailureExtras.failure(extras)
    return copy(
        failure = failure,
        isReconnecting = PlaybackFailureExtras.isRetrying(extras),
        // The service clears its failure when the listener pauses or a stream recovers;
        // the untyped flag set by onPlayerError must not outlive it.
        isError = isError && failure != null,
    )
}

/**
 * Applies a session metadata update. Only a genuinely resolved ICY track counts: until then
 * the service's title is the station name and its artist a generic description, both of
 * which the Listen screen already shows in their own places.
 */
internal fun RadioUiState.withMediaMetadata(metadata: MediaMetadata): RadioUiState {
    val extras = metadata.extras
    val resolved = extras?.getBoolean(RadioPlaybackService.EXTRA_HAS_RESOLVED_TRACK) == true
    return copy(
        trackTitle = metadata.title?.toString()?.takeIf { resolved },
        artist = metadata.artist?.toString()?.takeIf { resolved },
        albumArtUrl = extras?.getString(RadioPlaybackService.EXTRA_ALBUM_ART_URL)?.takeIf { resolved },
        trackViewUrl = extras?.getString(RadioPlaybackService.EXTRA_TRACK_VIEW_URL)?.takeIf { resolved },
    )
}
