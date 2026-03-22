package com.cascadiacollections.sir

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe circular byte buffer for DVR-style time-shift buffering of live audio.
 *
 * A background producer thread writes stream bytes via [write], while ExoPlayer's
 * loading thread reads via [read]. The read cursor can be moved backward with
 * [seekBack] to replay older audio, and snapped to the write cursor with [goLive].
 */
internal class CircularByteBuffer(val capacity: Int) {

    private val data = ByteArray(capacity)
    private var writePos = 0
    private var readPos = 0
    private var totalWritten = 0L

    private val lock = ReentrantLock()
    private val dataAvailable = lock.newCondition()

    /**
     * Write bytes into the buffer from the producer thread.
     * If the write cursor overtakes the read cursor, the read cursor is pushed forward.
     */
    fun write(src: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        lock.withLock {
            for (i in 0 until length) {
                data[writePos] = src[offset + i]
                writePos = (writePos + 1) % capacity

                // If write catches up to read while time-shifted, push read forward
                if (writePos == readPos && totalWritten >= capacity) {
                    readPos = (readPos + 1) % capacity
                }
            }
            totalWritten += length
            dataAvailable.signalAll()
        }
    }

    /**
     * Read bytes from the buffer. Blocks when no data is available.
     * Returns the number of bytes actually read, or -1 if interrupted.
     */
    fun read(dst: ByteArray, offset: Int, length: Int): Int {
        lock.withLock {
            while (available() == 0) {
                try {
                    dataAvailable.await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return -1
                }
            }
            val toRead = length.coerceAtMost(available())
            for (i in 0 until toRead) {
                dst[offset + i] = data[readPos]
                readPos = (readPos + 1) % capacity
            }
            return toRead
        }
    }

    /**
     * Move the read cursor backward by [bytes], allowing previously read data to be
     * re-read. Clamped to the amount of data available behind the read cursor.
     */
    fun seekBack(bytes: Int) {
        lock.withLock {
            val maxSeekBack = seekBackAvailable()
            val actual = bytes.coerceAtMost(maxSeekBack)
            readPos = ((readPos - actual) % capacity + capacity) % capacity
        }
    }

    /** Snap the read cursor to the write cursor (resume live playback). */
    fun goLive() {
        lock.withLock {
            readPos = writePos
        }
    }

    /** True when the read cursor is at the write cursor (no delay). */
    fun isLive(): Boolean = lock.withLock { readPos == writePos && totalWritten > 0 || totalWritten == 0L }

    /** Number of bytes available to read (ahead of read cursor). */
    fun available(): Int = lock.withLock {
        if (totalWritten == 0L) return@withLock 0
        val diff = writePos - readPos
        if (diff >= 0) diff else diff + capacity
    }

    /** Reset the buffer to its initial empty state. */
    fun clear() {
        lock.withLock {
            writePos = 0
            readPos = 0
            totalWritten = 0L
        }
    }

    /**
     * Number of bytes behind the read cursor that can be seeked back to.
     * This is the data that has been read but is still in the buffer.
     */
    private fun seekBackAvailable(): Int {
        // Total data in buffer = min(totalWritten, capacity)
        val totalInBuffer = totalWritten.coerceAtMost(capacity.toLong()).toInt()
        // Data ahead of read cursor
        val ahead = available()
        // Data behind read cursor
        return (totalInBuffer - ahead).coerceAtLeast(0)
    }
}
