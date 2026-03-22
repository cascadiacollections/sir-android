package com.cascadiacollections.sir

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlin.concurrent.thread

/**
 * A [DataSource] that proxies an upstream source through a [CircularByteBuffer],
 * enabling DVR-style time-shift on live streams.
 *
 * A background daemon thread continuously reads from the upstream into the buffer.
 * ExoPlayer's loading thread reads from the buffer via a movable read cursor.
 */
@UnstableApi
internal class TimeShiftDataSource(
    private val upstream: DataSource,
    private val buffer: CircularByteBuffer
) : DataSource {

    private var readerThread: Thread? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        upstream.open(dataSpec)
        // Do NOT clear the buffer here — buffered audio must survive player
        // stop/prepare cycles triggered by seekBack and goLive.

        readerThread = thread(isDaemon = true, name = "SIR-TimeShift") {
            val chunk = ByteArray(8192)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val bytesRead = upstream.read(chunk, 0, chunk.size)
                    if (bytesRead == C.RESULT_END_OF_INPUT) break
                    if (bytesRead > 0) {
                        buffer.write(chunk, 0, bytesRead)
                    }
                }
            } catch (_: InterruptedException) {
                // Expected on close
            } catch (_: Exception) {
                // Socket closed, HTTP errors, etc. — expected when DataSource is closed
                // during a seek or quality change
            }
        }

        return C.LENGTH_UNSET.toLong()
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        return buffer.read(target, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        readerThread?.interrupt()
        readerThread = null
        upstream.close()
    }

    fun seekBack(bytes: Int) = buffer.seekBack(bytes)
    fun goLive() = buffer.goLive()
    fun isLive() = buffer.isLive()

    /**
     * Factory that creates [TimeShiftDataSource] instances wrapping an upstream factory.
     * Exposes [lastCreated] so the service can access seek/goLive controls.
     */
    @UnstableApi
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val buffer: CircularByteBuffer
    ) : DataSource.Factory {

        @Volatile
        var lastCreated: TimeShiftDataSource? = null
            private set

        override fun createDataSource(): TimeShiftDataSource {
            val upstream = upstreamFactory.createDataSource()
            return TimeShiftDataSource(upstream, buffer).also { lastCreated = it }
        }
    }
}
