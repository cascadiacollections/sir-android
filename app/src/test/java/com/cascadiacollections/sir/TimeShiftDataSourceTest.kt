package com.cascadiacollections.sir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class TimeShiftDataSourceTest {

    /**
     * Simulates TimeShiftDataSource behavior using CircularByteBuffer directly,
     * since the DataSource API requires Media3 dependencies not available in unit tests.
     * This tests the core buffer mechanics that TimeShiftDataSource delegates to.
     */

    @Test
    fun `read returns bytes written by producer`() {
        val buffer = CircularByteBuffer(1024)
        val src = byteArrayOf(1, 2, 3, 4, 5)
        val latch = CountDownLatch(1)
        val dst = ByteArray(5)
        var bytesRead = 0

        // Simulate ExoPlayer reading thread
        thread {
            bytesRead = buffer.read(dst, 0, 5)
            latch.countDown()
        }

        // Simulate background producer writing upstream bytes
        Thread.sleep(20)
        buffer.write(src, 0, src.size)

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertArrayEquals(src, dst)
        assertEquals(5, bytesRead)
    }

    @Test
    fun `seekBack causes old bytes to be re-read`() {
        val buffer = CircularByteBuffer(1024)
        val src = ByteArray(20) { it.toByte() }
        buffer.write(src, 0, 20)

        // Read first 10 bytes
        val first = ByteArray(10)
        buffer.read(first, 0, 10)
        assertArrayEquals(ByteArray(10) { it.toByte() }, first)

        // Seek back 5 bytes
        buffer.seekBack(5)

        // Should re-read bytes 5-9
        val replayed = ByteArray(5)
        buffer.read(replayed, 0, 5)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8, 9), replayed)
    }

    @Test
    fun `goLive resumes reading new bytes`() {
        val buffer = CircularByteBuffer(1024)
        buffer.write(ByteArray(20) { it.toByte() }, 0, 20)

        // Read 10, seek back 5
        buffer.read(ByteArray(10), 0, 10)
        buffer.seekBack(5)
        assertFalse(buffer.isLive())

        // Go live - should skip ahead
        buffer.goLive()
        assertTrue(buffer.isLive())
        assertEquals(0, buffer.available()) // at write cursor, nothing ahead

        // New data written should be readable
        buffer.write(byteArrayOf(99), 0, 1)
        val dst = ByteArray(1)
        buffer.read(dst, 0, 1)
        assertEquals(99.toByte(), dst[0])
    }

    @Test
    fun `isLive state transitions`() {
        val buffer = CircularByteBuffer(1024)
        assertTrue("Initially live", buffer.isLive())

        buffer.write(ByteArray(10), 0, 10)
        buffer.read(ByteArray(10), 0, 10)
        assertTrue("After reading all, live", buffer.isLive())

        buffer.seekBack(5)
        assertFalse("After seekBack, not live", buffer.isLive())

        buffer.goLive()
        assertTrue("After goLive, live again", buffer.isLive())
    }

    private fun assertEquals(expected: Any, actual: Any) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
