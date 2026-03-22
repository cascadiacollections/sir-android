package com.cascadiacollections.sir

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
    fun `write and read round-trip`() {
        val buffer = CircularByteBuffer(64)
        val src = byteArrayOf(1, 2, 3, 4, 5)
        buffer.write(src, 0, src.size)

        val dst = ByteArray(5)
        val read = buffer.read(dst, 0, 5)
        assertEquals(5, read)
        assertArrayEquals(src, dst)
    }

    @Test
    fun `available returns correct count after write`() {
        val buffer = CircularByteBuffer(64)
        assertEquals(0, buffer.available())
        buffer.write(ByteArray(10), 0, 10)
        assertEquals(10, buffer.available())
    }

    @Test
    fun `available decreases after read`() {
        val buffer = CircularByteBuffer(64)
        buffer.write(ByteArray(10), 0, 10)
        buffer.read(ByteArray(4), 0, 4)
        assertEquals(6, buffer.available())
    }

    @Test
    fun `read blocks when empty and unblocks on write`() {
        val buffer = CircularByteBuffer(64)
        val latch = CountDownLatch(1)
        val dst = ByteArray(3)
        var bytesRead = 0

        thread {
            bytesRead = buffer.read(dst, 0, 3)
            latch.countDown()
        }

        // Give reader time to block
        Thread.sleep(50)
        assertFalse("Reader should still be blocked", latch.await(10, TimeUnit.MILLISECONDS))

        buffer.write(byteArrayOf(10, 20, 30), 0, 3)
        assertTrue("Reader should unblock", latch.await(1, TimeUnit.SECONDS))
        assertEquals(3, bytesRead)
        assertArrayEquals(byteArrayOf(10, 20, 30), dst)
    }

    @Test
    fun `wrap-around write and read`() {
        val buffer = CircularByteBuffer(8)
        // Fill buffer
        buffer.write(ByteArray(6) { it.toByte() }, 0, 6)
        // Read 4, freeing space
        buffer.read(ByteArray(4), 0, 4)
        // Write 5 more, wrapping around
        val src = ByteArray(5) { (10 + it).toByte() }
        buffer.write(src, 0, 5)

        // Should read the remaining 2 from first write + 5 from second = 7
        val dst = ByteArray(7)
        val read = buffer.read(dst, 0, 7)
        assertEquals(7, read)
        assertEquals(4.toByte(), dst[0]) // remaining from first write
        assertEquals(5.toByte(), dst[1])
        assertEquals(10.toByte(), dst[2]) // second write
    }

    @Test
    fun `seekBack moves read cursor backward`() {
        val buffer = CircularByteBuffer(64)
        val src = ByteArray(20) { it.toByte() }
        buffer.write(src, 0, 20)

        // Read 10 bytes
        buffer.read(ByteArray(10), 0, 10)
        assertEquals(10, buffer.available())

        // Seek back 5
        buffer.seekBack(5)
        assertEquals(15, buffer.available())

        // Read should return bytes starting from position 5
        val dst = ByteArray(5)
        buffer.read(dst, 0, 5)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8, 9), dst)
    }

    @Test
    fun `seekBack clamped to available data behind cursor`() {
        val buffer = CircularByteBuffer(64)
        buffer.write(ByteArray(10), 0, 10)
        buffer.read(ByteArray(5), 0, 5) // read 5, 5 behind, 5 ahead

        // Try to seek back 100, should clamp to 5
        buffer.seekBack(100)
        assertEquals(10, buffer.available()) // all 10 bytes available again
    }

    @Test
    fun `goLive snaps read cursor to write cursor`() {
        val buffer = CircularByteBuffer(64)
        buffer.write(ByteArray(20), 0, 20)
        buffer.read(ByteArray(10), 0, 10)
        buffer.seekBack(5) // now 15 available

        buffer.goLive()
        assertEquals(0, buffer.available())
        assertTrue(buffer.isLive())
    }

    @Test
    fun `isLive true initially`() {
        val buffer = CircularByteBuffer(64)
        assertTrue(buffer.isLive())
    }

    @Test
    fun `isLive false after seekBack`() {
        val buffer = CircularByteBuffer(64)
        buffer.write(ByteArray(20), 0, 20)
        buffer.read(ByteArray(20), 0, 20)
        assertTrue(buffer.isLive())

        buffer.seekBack(5)
        assertFalse(buffer.isLive())
    }

    @Test
    fun `isLive true after goLive`() {
        val buffer = CircularByteBuffer(64)
        buffer.write(ByteArray(20), 0, 20)
        buffer.read(ByteArray(10), 0, 10)
        buffer.seekBack(5)
        assertFalse(buffer.isLive())

        buffer.goLive()
        assertTrue(buffer.isLive())
    }

    @Test
    fun `write overtakes read cursor pushes read forward`() {
        val buffer = CircularByteBuffer(8)
        // Write 8 bytes to fill buffer, read cursor at 0
        buffer.write(ByteArray(8) { it.toByte() }, 0, 8)
        // Now write 3 more — read cursor must be pushed forward
        buffer.write(byteArrayOf(100, 101, 102), 0, 3)

        // Available should be less than capacity (read was pushed)
        val avail = buffer.available()
        assertTrue("Available ($avail) should be < capacity", avail < 8)

        // Read should NOT get bytes 0,1,2 (they were overwritten)
        val dst = ByteArray(avail)
        buffer.read(dst, 0, avail)
        // First byte should be 3 or later (0,1,2 overwritten)
        assertTrue("First readable byte should be >= 3", dst[0] >= 3)
    }

    @Test
    fun `concurrent write and read no corruption`() {
        // Buffer must be larger than total to avoid overwrites during concurrent access
        val totalBytes = 5000
        val buffer = CircularByteBuffer(totalBytes + 1024)
        val written = ByteArray(totalBytes) { (it % 256).toByte() }
        val readBytes = ByteArray(totalBytes)
        var totalRead = 0
        val latch = CountDownLatch(1)

        thread {
            var pos = 0
            while (pos < totalBytes) {
                val n = buffer.read(readBytes, pos, (totalBytes - pos).coerceAtMost(100))
                if (n > 0) pos += n
            }
            totalRead = pos
            latch.countDown()
        }

        // Writer
        var pos = 0
        while (pos < totalBytes) {
            val chunk = (totalBytes - pos).coerceAtMost(73) // odd chunk size
            buffer.write(written, pos, chunk)
            pos += chunk
        }

        assertTrue("Reader should finish", latch.await(5, TimeUnit.SECONDS))
        assertEquals(totalBytes, totalRead)
        assertArrayEquals(written, readBytes)
    }

    @Test
    fun `clear resets buffer`() {
        val buffer = CircularByteBuffer(64)
        buffer.write(ByteArray(20), 0, 20)
        buffer.read(ByteArray(10), 0, 10)

        buffer.clear()
        assertEquals(0, buffer.available())
        assertTrue(buffer.isLive())
    }

    @Test
    fun `zero-length write is no-op`() {
        val buffer = CircularByteBuffer(64)
        buffer.write(ByteArray(0), 0, 0)
        assertEquals(0, buffer.available())
        assertTrue(buffer.isLive())
    }

    @Test
    fun `read returns only available bytes when requested more`() {
        val buffer = CircularByteBuffer(64)
        buffer.write(byteArrayOf(1, 2, 3), 0, 3)

        val dst = ByteArray(10)
        val read = buffer.read(dst, 0, 10)
        assertEquals(3, read)
    }
}
