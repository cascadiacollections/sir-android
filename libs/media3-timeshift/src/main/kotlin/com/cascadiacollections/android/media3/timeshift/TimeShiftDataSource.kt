package com.cascadiacollections.android.media3.timeshift

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlin.concurrent.thread

private const val TAG = "TimeShiftDataSource"

/**
 * A [DataSource] that proxies an upstream source through a [TimeShiftController]'s
 * buffer, enabling DVR-style time-shift on live streams.
 *
 * A background daemon thread continuously reads from the upstream into the buffer.
 * The consumer thread reads from the buffer via a movable read cursor; seeking and
 * returning to live are driven through the [TimeShiftController], not through this
 * data source, so callers keep working across the data sources a player creates and
 * discards over the life of a stream.
 *
 * When the upstream ends or fails, the buffer is marked end-of-stream so the consumer
 * drains what is buffered and then sees [C.RESULT_END_OF_INPUT] rather than blocking.
 *
 * @param upstream The upstream [DataSource] to read from.
 * @param controller Controller owning the buffer these bytes stream into.
 * @param threadName Name for the background reader thread.
 * @param chunkSize Size of the read buffer used by the background thread.
 */
@UnstableApi
class TimeShiftDataSource(
    private val upstream: DataSource,
    private val controller: TimeShiftController,
    private val threadName: String = "TimeShift",
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : DataSource {

    private val buffer: CircularByteBuffer get() = controller.buffer

    private var readerThread: Thread? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        upstream.open(dataSpec)

        // A previous data source over this buffer may have ended it; this one is live again.
        buffer.resumeStream()

        readerThread = thread(isDaemon = true, name = threadName) {
            val chunk = ByteArray(chunkSize)
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
            } catch (e: Exception) {
                Log.w(TAG, "TimeShift reader stopped", e)
            } finally {
                // Whether the upstream ended, failed or was interrupted, no more bytes
                // are coming — release any consumer blocked waiting for them.
                buffer.signalEndOfStream()
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
        buffer.signalEndOfStream()
        upstream.close()
    }

    /**
     * Factory that creates [TimeShiftDataSource] instances wrapping an upstream factory.
     *
     * Seek and go-live controls live on [controller], which stays valid regardless of
     * how many data sources the player creates.
     *
     * @param upstreamFactory Factory for creating upstream [DataSource] instances.
     * @param controller Controller shared by all created data sources.
     * @param threadName Name for the background reader thread.
     * @param chunkSize Size of the read buffer used by the background thread.
     */
    @UnstableApi
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val controller: TimeShiftController,
        private val threadName: String = "TimeShift",
        private val chunkSize: Int = DEFAULT_CHUNK_SIZE
    ) : DataSource.Factory {

        override fun createDataSource(): TimeShiftDataSource {
            val upstream = upstreamFactory.createDataSource()
            return TimeShiftDataSource(upstream, controller, threadName, chunkSize)
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 8192
    }
}
