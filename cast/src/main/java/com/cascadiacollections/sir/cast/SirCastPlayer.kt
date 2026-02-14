package com.cascadiacollections.sir.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext

/**
 * Manages CastPlayer lifecycle and provides seamless switching between
 * local playback and Chromecast.
 */
@UnstableApi
class SirCastPlayer(context: Context) : SessionAvailabilityListener {

    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null

    private var onCastSessionStarted: ((CastPlayer) -> Unit)? = null
    private var onCastSessionEnded: (() -> Unit)? = null

    init {
        try {
            castContext = CastContext.getSharedInstance(context)
            castPlayer = CastPlayer(castContext!!).apply {
                setSessionAvailabilityListener(this@SirCastPlayer)
            }
        } catch (e: Exception) {
            // Cast not available on this device
            castContext = null
            castPlayer = null
        }
    }

    /**
     * Get the CastPlayer if a cast session is available
     */
    fun getCastPlayer(): CastPlayer? = castPlayer

    /**
     * Check if currently casting
     */
    fun isCasting(): Boolean = castPlayer?.isCastSessionAvailable == true

    /**
     * Set callbacks for cast session state changes
     */
    fun setSessionCallbacks(
        onStarted: (CastPlayer) -> Unit,
        onEnded: () -> Unit
    ) {
        onCastSessionStarted = onStarted
        onCastSessionEnded = onEnded
    }

    /**
     * Transfer playback to cast device with current media item
     */
    fun transferToCast(
        streamUrl: String,
        title: String,
        artist: String?,
        isPlaying: Boolean
    ) {
        val castPlayer = this.castPlayer ?: return
        if (!isCasting()) return

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setIsPlayable(true)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .setMimeType("audio/mpeg")
            .build()

        castPlayer.setMediaItem(mediaItem)
        castPlayer.prepare()
        castPlayer.playWhenReady = isPlaying
    }

    /**
     * Stop casting and return playback state
     */
    fun stopCasting(): Boolean {
        val wasPlaying = castPlayer?.isPlaying == true
        castPlayer?.stop()
        castPlayer?.clearMediaItems()
        return wasPlaying
    }

    override fun onCastSessionAvailable() {
        castPlayer?.let { onCastSessionStarted?.invoke(it) }
    }

    override fun onCastSessionUnavailable() {
        onCastSessionEnded?.invoke()
    }

    fun release() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        castContext = null
    }
}

