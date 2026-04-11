package com.cascadiacollections.android.media3.timeshift

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CircularByteBufferTest {

    @Test
    fun `write and read basic bytes`() {
        val buf = CircularByteBuffer(100)
        val src = byteArrayOf(1, 2, 3, 4, 5)
        buf.write(src, 0, 5)
        val dst = ByteArray(5)
        val read = buf.read(dst, 0, 5)
        assertEquals(5, read)
        assertArrayEquals(src, dst)
    }

    @Test
    fun `available tracks unread bytes`() {
        val buf = CircularByteBuffer(100)
        assertEquals(0, buf.available())
        buf.write(byteArrayOf(1, 2, 3), 0, 3)
        assertEquals(3, buf.available())
        buf.read(ByteArray(2), 0, 2)
        assertEquals(1, buf.available())
    }

    @Test
    fun `seekBack replays previously read data`() {
        val buf = CircularByteBuffer(100)
        buf.write(ByteArray(20) { it.toByte() }, 0, 20)
        buf.read(ByteArray(10), 0, 10)
        buf.seekBack(5)
        val replayed = ByteArray(5)
        buf.read(replayed, 0, 5)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8, 9), replayed)
    }

    @Test
    fun `goLive snaps to write cursor`() {
        val buf = CircularByteBuffer(100)
        buf.write(ByteArray(20) { it.toByte() }, 0, 20)
        buf.read(ByteArray(10), 0, 10)
        buf.seekBack(5)
        assertFalse(buf.isLive())
        buf.goLive()
        assertTrue(buf.isLive())
        assertEquals(0, buf.available())
    }

    @Test
    fun `isLive state transitions`() {
        val buf = CircularByteBuffer(1024)
        assertTrue(buf.isLive())
        buf.write(ByteArray(10), 0, 10)
        buf.read(ByteArray(10), 0, 10)
        assertTrue(buf.isLive())
        buf.seekBack(5)
        assertFalse(buf.isLive())
        buf.goLive()
        assertTrue(buf.isLive())
    }

    @Test
    fun `canSeekBack returns false on empty buffer`() {
        assertFalse(CircularByteBuffer(10).canSeekBack(1))
    }

    @Test
    fun `canSeekBack returns true after read`() {
        val buf = CircularByteBuffer(100)
        buf.write(ByteArray(10) { it.toByte() }, 0, 10)
        buf.read(ByteArray(10), 0, 10)
        assertTrue(buf.canSeekBack(5))
        assertTrue(buf.canSeekBack(10))
        assertFalse(buf.canSeekBack(11))
    }

    @Test
    fun `clear resets all state`() {
        val buf = CircularByteBuffer(100)
        buf.write(byteArrayOf(1, 2, 3, 4, 5), 0, 5)
        buf.clear()
        assertEquals(0, buf.available())
        assertTrue(buf.isLive())
        assertFalse(buf.canSeekBack(1))
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
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(5, bytesRead)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), dst)
    }

    @Test
    fun `wrap-around write maintains correct available count`() {
        val buf = CircularByteBuffer(8)
        buf.write(byteArrayOf(1, 2, 3, 4, 5, 6), 0, 6)
        buf.read(ByteArray(4), 0, 4)
        assertEquals(2, buf.available())
        buf.write(byteArrayOf(7, 8, 9, 10), 0, 4)
        assertEquals(6, buf.available())
    }

    @Test
    fun `write with length 0 is a no-op`() {
        val buf = CircularByteBuffer(10)
        buf.write(byteArrayOf(1, 2, 3), 0, 0)
        assertEquals(0, buf.available())
    }

    @Test
    fun `seekBackAvailable returns 0 on empty buffer`() {
        assertEquals(0, CircularByteBuffer(10).seekBackAvailable())
    }

    @Test
    fun `seekBackAvailable returns correct value after partial read`() {
        val buf = CircularByteBuffer(100)
        buf.write(ByteArray(10) { it.toByte() }, 0, 10)
        buf.read(ByteArray(4), 0, 4)
        assertEquals(4, buf.seekBackAvailable())
    }

    @Test
    fun `capacity property is accessible`() {
        assertEquals(256, CircularByteBuffer(256).capacity)
    }

    @Test
    fun `read after seekBack returns correct data`() {
        val buf = CircularByteBuffer(100)
        buf.write(byteArrayOf(10, 20, 30, 40, 50), 0, 5)
        buf.read(ByteArray(5), 0, 5)
        buf.seekBack(3)
        val replayed = ByteArray(3)
        buf.read(replayed, 0, 3)
        assertEquals(30.toByte(), replayed[0])
        assertEquals(40.toByte(), replayed[1])
        assertEquals(50.toByte(), replayed[2])
    }
}
