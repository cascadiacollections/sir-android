package com.cascadiacollections.sir.cast

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cascadiacollections.sir.RadioPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val TAG = "CastSessionCoordinator"

/**
 * Bridges [RadioPlaybackService]'s local playback session to a Chromecast session.
 *
 * This lives entirely in the `:cast` module rather than the service itself: `:cast`
 * depends on `:app` (the reverse of a normal dependency — required for a dynamic
 * feature module, which can never be depended on by the base module it extends), so
 * this is the only direction code can flow. `RadioPlaybackService` knows nothing about
 * this class or about Chromecast at all; it only exposes the same public
 * [MediaController] API [com.cascadiacollections.sir.RadioViewModel] already uses to
 * drive the phone UI, plus (via `MediaMetadata.extras`) the one extra piece a
 * same-process-but-different [MediaController] can't otherwise reach: the actual
 * playable stream URL, since `MediaItem.LocalConfiguration` never crosses a
 * MediaController boundary by Media3's own design.
 */
@UnstableApi
class CastSessionCoordinator(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private val castPlayer = SirCastPlayer(context)

    // Whether local playback was active at the moment casting started, so ending the
    // cast session resumes exactly what the user had going rather than always playing
    // or always staying paused.
    private var wasPlayingBeforeCast = false

    init {
        castPlayer.setSessionCallbacks(
            onStarted = { _ -> onCastSessionAvailable() },
            onEnded = { onCastSessionUnavailable() }
        )
        connect()
    }

    private fun connect() {
        val sessionToken = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        scope.launch {
            try {
                controller = MediaController.Builder(context, sessionToken).buildAsync().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to the playback session", e)
            }
        }
    }

    private fun onCastSessionAvailable() {
        val activeController = controller ?: return
        val metadata = activeController.mediaMetadata
        val streamUrl = metadata.extras?.getString(RadioPlaybackService.EXTRA_STREAM_URL)
        val title = metadata.title?.toString()
        if (streamUrl.isNullOrBlank() || title.isNullOrBlank()) return

        wasPlayingBeforeCast = activeController.isPlaying
        castPlayer.transferToCast(
            streamUrl = streamUrl,
            title = title,
            artist = metadata.artist?.toString(),
            isPlaying = wasPlayingBeforeCast
        )
        activeController.pause()
    }

    private fun onCastSessionUnavailable() {
        if (wasPlayingBeforeCast) {
            controller?.play()
        }
        wasPlayingBeforeCast = false
    }

    fun release() {
        castPlayer.release()
        controller?.release()
        controller = null
    }
}
