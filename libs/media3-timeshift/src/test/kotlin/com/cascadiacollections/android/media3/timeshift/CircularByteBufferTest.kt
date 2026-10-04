package com.cascadiacollections.android.media3.timeshift

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Test

class CircularByteBufferTest {

    @Test
    fun `write and read basic bytes`() {
        val buf = CircularByteBuffer(100)
        val src = byteArrayOf(1, 2, 3, 4, 5)
        buf.write(src, 0, 5)
        val dst = ByteArray(5)
        val read = buf.read(dst, 0, 5)
        assertThat(read).isEqualTo(5)
        assertThat(dst).isEqualTo(src)
    }

    @Test
    fun `available tracks unread bytes`() {
        val buf = CircularByteBuffer(100)
        assertThat(buf.available()).isEqualTo(0)
        buf.write(byteArrayOf(1, 2, 3), 0, 3)
        assertThat(buf.available()).isEqualTo(3)
        buf.read(ByteArray(2), 0, 2)
        assertThat(buf.available()).isEqualTo(1)
    }

    @Test
    fun `available tracks exact full buffer`() {
        val buf = CircularByteBuffer(4)
        buf.write(byteArrayOf(1, 2, 3, 4), 0, 4)
        assertThat(buf.available()).isEqualTo(4)

        val dst = ByteArray(4)
        assertThat(buf.read(dst, 0, 4)).isEqualTo(4)
        assertThat(dst).isEqualTo(byteArrayOf(1, 2, 3, 4))
        assertThat(buf.available()).isEqualTo(0)
    }

    @Test
    fun `overwriting full buffer drops oldest unread bytes`() {
        val buf = CircularByteBuffer(4)
        buf.write(byteArrayOf(1, 2, 3, 4, 5, 6), 0, 6)

        val dst = ByteArray(4)
        assertThat(buf.read(dst, 0, 4)).isEqualTo(4)
        assertThat(dst).isEqualTo(byteArrayOf(3, 4, 5, 6))
    }

    @Test
    fun `seekBack replays previously read data`() {
        val buf = CircularByteBuffer(100)
        buf.write(ByteArray(20) { it.toByte() }, 0, 20)
        buf.read(ByteArray(10), 0, 10)
        buf.seekBack(5)
        val replayed = ByteArray(5)
        buf.read(replayed, 0, 5)
        assertThat(replayed).isEqualTo(byteArrayOf(5, 6, 7, 8, 9))
    }

    @Test
    fun `goLive snaps to write cursor`() {
        val buf = CircularByteBuffer(100)
        buf.write(ByteArray(20) { it.toByte() }, 0, 20)
        buf.read(ByteArray(10), 0, 10)
        buf.seekBack(5)
        assertThat(buf.isLive()).isFalse()
        buf.goLive()
        assertThat(buf.isLive()).isTrue()
        assertThat(buf.available()).isEqualTo(0)
    }

    @Test
    fun `isLive state transitions`() {
        val buf = CircularByteBuffer(1024)
        assertThat(buf.isLive()).isTrue()
        buf.write(ByteArray(10), 0, 10)
        buf.read(ByteArray(10), 0, 10)
        assertThat(buf.isLive()).isTrue()
        buf.seekBack(5)
        assertThat(buf.isLive()).isFalse()
        buf.goLive()
        assertThat(buf.isLive()).isTrue()
    }

    @Test
    fun `canSeekBack returns false on empty buffer`() {
        assertThat(CircularByteBuffer(10).canSeekBack(1)).isFalse()
    }

    @Test
    fun `canSeekBack returns true after read`() {
        val buf = CircularByteBuffer(100)
        buf.write(ByteArray(10) { it.toByte() }, 0, 10)
        buf.read(ByteArray(10), 0, 10)
        assertThat(buf.canSeekBack(5)).isTrue()
        assertThat(buf.canSeekBack(10)).isTrue()
        assertThat(buf.canSeekBack(11)).isFalse()
    }

    @Test
    fun `clear resets all state`() {
        val buf = CircularByteBuffer(100)
        buf.write(byteArrayOf(1, 2, 3, 4, 5), 0, 5)
        buf.clear()
        assertThat(buf.available()).isEqualTo(0)
        assertThat(buf.isLive()).isTrue()
        assertThat(buf.canSeekBack(1)).isFalse()
    }

    @Test
    fun `read blocks until data available`() {
        val buf = CircularByteBuffer(1024)
        val latch = CountDownLatch(1)
        val dst = ByteArray(5)
        var bytesRead = 0

        thread {
            bytesRead = buf.read(dst, 0, 5)
            latch.countDown()
        }

        Thread.sleep(20)
        buf.write(byteArrayOf(1, 2, 3, 4, 5), 0, 5)
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()
        assertThat(bytesRead).isEqualTo(5)
        assertThat(dst).isEqualTo(byteArrayOf(1, 2, 3, 4, 5))
    }

    @Test
    fun `wrap-around write maintains correct available count`() {
        val buf = CircularByteBuffer(8)
        buf.write(byteArrayOf(1, 2, 3, 4, 5, 6), 0, 6)
        buf.read(ByteArray(4), 0, 4)
        assertThat(buf.available()).isEqualTo(2)
        buf.write(byteArrayOf(7, 8, 9, 10), 0, 4)
        assertThat(buf.available()).isEqualTo(6)
    }

    @Test
    fun `write with length 0 is a no-op`() {
        val buf = CircularByteBuffer(10)
        buf.write(byteArrayOf(1, 2, 3), 0, 0)
        assertThat(buf.available()).isEqualTo(0)
    }

    @Test
    fun `read with length 0 is a no-op`() {
        val buf = CircularByteBuffer(10)
        buf.write(byteArrayOf(1, 2, 3), 0, 3)
        assertThat(buf.read(ByteArray(0), 0, 0)).isEqualTo(0)
        assertThat(buf.available()).isEqualTo(3)
    }

    @Test
    fun `seekBackAvailable returns 0 on empty buffer`() {
        assertThat(CircularByteBuffer(10).seekBackAvailable()).isEqualTo(0)
    }

    @Test
    fun `seekBackAvailable returns correct value after partial read`() {
        val buf = CircularByteBuffer(100)
        buf.write(ByteArray(10) { it.toByte() }, 0, 10)
        buf.read(ByteArray(4), 0, 4)
        assertThat(buf.seekBackAvailable()).isEqualTo(4)
    }

    @Test
    fun `capacity property is accessible`() {
        assertThat(CircularByteBuffer(256).capacity).isEqualTo(256)
    }

    @Test
    fun `read after seekBack returns correct data`() {
        val buf = CircularByteBuffer(100)
        buf.write(byteArrayOf(10, 20, 30, 40, 50), 0, 5)
        buf.read(ByteArray(5), 0, 5)
        buf.seekBack(3)
        val replayed = ByteArray(3)
        buf.read(replayed, 0, 3)
        assertThat(replayed[0]).isEqualTo(30.toByte())
        assertThat(replayed[1]).isEqualTo(40.toByte())
        assertThat(replayed[2]).isEqualTo(50.toByte())
    }

    @Test
    fun `read returns END_OF_STREAM once the producer ends and the buffer drains`() {
        val buf = CircularByteBuffer(100)
        buf.write(byteArrayOf(1, 2, 3), 0, 3)
        buf.signalEndOfStream()

        val dst = ByteArray(3)
        assertThat(buf.read(dst, 0, 3)).isEqualTo(3)
        assertThat(buf.read(dst, 0, 3)).isEqualTo(CircularByteBuffer.END_OF_STREAM)
    }

    @Test
    fun `signalEndOfStream unblocks a reader waiting on an empty buffer`() {
        val buf = CircularByteBuffer(100)
        val result = java.util.concurrent.atomic.AtomicInteger(Int.MIN_VALUE)
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)

        val reader = thread {
            started.countDown()
            result.set(buf.read(ByteArray(16), 0, 16))
            finished.countDown()
        }

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue()
        buf.signalEndOfStream()

        assertThat(finished.await(2, TimeUnit.SECONDS), name = "reader stayed blocked after end of stream").isTrue()
        assertThat(result.get()).isEqualTo(CircularByteBuffer.END_OF_STREAM)
        reader.join()
    }

    @Test
    fun `data buffered before end of stream can still be replayed`() {
        val buf = CircularByteBuffer(100)
        val src = ByteArray(20) { it.toByte() }
        buf.write(src, 0, 20)
        buf.read(ByteArray(20), 0, 20)
        buf.signalEndOfStream()

        buf.seekBack(10)
        val dst = ByteArray(10)
        assertThat(buf.read(dst, 0, 10)).isEqualTo(10)
        assertThat(dst).isEqualTo(src.copyOfRange(10, 20))
    }

    @Test
    fun `resumeStream makes reads block for new data again`() {
        val buf = CircularByteBuffer(100)
        buf.signalEndOfStream()
        assertThat(buf.isEndOfStream()).isTrue()

        buf.resumeStream()

        assertThat(buf.isEndOfStream()).isFalse()
        buf.write(byteArrayOf(7), 0, 1)
        assertThat(buf.read(ByteArray(1), 0, 1)).isEqualTo(1)
    }

    @Test
    fun `clear resets the end of stream signal`() {
        val buf = CircularByteBuffer(100)
        buf.signalEndOfStream()

        buf.clear()

        assertThat(buf.isEndOfStream()).isFalse()
    }
}
