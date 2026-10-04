package com.cascadiacollections.sir

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Sends HLS items to [hls] and everything else to [progressive].
 *
 * The progressive path reads through the time-shift buffer, which is one shared byte
 * ring fed by whichever data source is open. That models a single continuous Icecast
 * connection; HLS opens a playlist and a run of segments, often concurrently, and their
 * bytes would interleave in the ring. So HLS goes straight to the network instead and
 * simply has no replay buffer.
 */
@UnstableApi
class StreamMediaSourceFactory(private val progressive: MediaSource.Factory, private val hls: MediaSource.Factory) :
    MediaSource.Factory {

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider
    ): MediaSource.Factory = apply {
        progressive.setDrmSessionManagerProvider(drmSessionManagerProvider)
        hls.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory =
        apply {
            progressive.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            hls.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        }

    override fun getSupportedTypes(): IntArray =
        (progressive.supportedTypes + hls.supportedTypes).distinct().toIntArray()

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val local = mediaItem.localConfiguration
        val isHls = local != null &&
            Util.inferContentTypeForUriAndMimeType(local.uri, local.mimeType) == C.CONTENT_TYPE_HLS
        return (if (isHls) hls else progressive).createMediaSource(mediaItem)
    }
}
