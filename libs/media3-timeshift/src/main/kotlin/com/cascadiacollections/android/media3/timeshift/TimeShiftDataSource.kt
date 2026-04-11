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
 * A [DataSource] that proxies an upstream source through a [CircularByteBuffer],
 * enabling DVR-style time-shift on live streams.
 *
 * A background daemon thread continuously reads from the upstream into the buffer.
 * The consumer thread reads from the buffer via a movable read cursor.
 *
 * @param upstream The upstream [DataSource] to read from.
 * @param buffer The [CircularByteBuffer] used for buffering.
 * @param threadName Name for the background reader thread.
 * @param chunkSize Size of the read buffer used by the background thread.
 */
@UnstableApi
class TimeShiftDataSource(
    private val upstream: DataSource,
    private val buffer: CircularByteBuffer,
    private val threadName: String = "TimeShift",
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : DataSource {

    private var readerThread: Thread? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        upstream.open(dataSpec)

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
     *
     * @param upstreamFactory Factory for creating upstream [DataSource] instances.
     * @param buffer Shared [CircularByteBuffer] for all created data sources.
     * @param threadName Name for the background reader thread.
     * @param chunkSize Size of the read buffer used by the background thread.
     */
    @UnstableApi
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val buffer: CircularByteBuffer,
        private val threadName: String = "TimeShift",
        private val chunkSize: Int = DEFAULT_CHUNK_SIZE
    ) : DataSource.Factory {

        @Volatile
        var lastCreated: TimeShiftDataSource? = null
            private set

        override fun createDataSource(): TimeShiftDataSource {
            val upstream = upstreamFactory.createDataSource()
            return TimeShiftDataSource(upstream, buffer, threadName, chunkSize).also { lastCreated = it }
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 8192
    }
}
