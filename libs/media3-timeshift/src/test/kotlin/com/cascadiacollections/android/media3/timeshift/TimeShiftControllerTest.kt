package com.cascadiacollections.android.media3.timeshift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TimeShiftControllerTest {

    /** 8 bytes per second keeps the byte/second arithmetic readable in assertions. */
    private fun controller(capacityBytes: Int = 80) =
        TimeShiftController(capacityBytes = capacityBytes, bytesPerSecond = 8)

    private fun TimeShiftController.playSeconds(seconds: Int) {
        val bytes = seconds * 8
        buffer.write(ByteArray(bytes) { it.toByte() }, 0, bytes)
        buffer.read(ByteArray(bytes), 0, bytes)
    }

    @Test
    fun `rejects non-positive byte rate`() {
        listOf(0, -1).forEach { rate ->
            val error = runCatching { TimeShiftController(1024, rate) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        }
    }

    @Test
    fun `maxSeekBack reflects capacity at the stream byte rate`() {
        assertEquals(10.seconds, controller(capacityBytes = 80).maxSeekBack)
    }

    @Test
    fun `availableSeekBack grows as audio plays`() {
        val controller = controller()
        assertEquals(Duration.ZERO, controller.availableSeekBack)

        controller.playSeconds(4)
        assertEquals(4.seconds, controller.availableSeekBack)
    }

    @Test
    fun `availableSeekBack is capped by buffer capacity`() {
        val controller = controller(capacityBytes = 80)
        controller.playSeconds(30)
        assertEquals(controller.maxSeekBack, controller.availableSeekBack)
    }

    @Test
    fun `canSeekBack is false until enough audio is buffered`() {
        val controller = controller()
        assertFalse(controller.canSeekBack(5.seconds))

        controller.playSeconds(5)
        assertTrue(controller.canSeekBack(5.seconds))
    }

    @Test
    fun `seekBack reports failure and leaves playback untouched when under-buffered`() {
        val controller = controller()
        controller.playSeconds(2)

        assertFalse(controller.seekBack(5.seconds))
        assertTrue(controller.isLive)
        assertEquals(2.seconds, controller.availableSeekBack)
    }

    @Test
    fun `seekBack replays the requested duration`() {
        val controller = controller()
        controller.playSeconds(6)
        assertTrue(controller.isLive)

        assertTrue(controller.seekBack(4.seconds))

        assertFalse(controller.isLive)
        // 4s of audio is queued up to be played a second time.
        assertEquals(4 * 8, controller.buffer.available())
        assertEquals(2.seconds, controller.availableSeekBack)
    }

    @Test
    fun `goLive returns to the live edge`() {
        val controller = controller()
        controller.playSeconds(6)
        controller.seekBack(4.seconds)

        controller.goLive()

        assertTrue(controller.isLive)
        assertEquals(0, controller.buffer.available())
    }

    @Test
    fun `zero and negative durations seek nowhere but still succeed`() {
        val controller = controller()
        controller.playSeconds(3)

        assertTrue(controller.seekBack(Duration.ZERO))
        assertTrue(controller.seekBack((-5).seconds))
        assertTrue(controller.isLive)
    }

    @Test
    fun `reset drops buffered audio`() {
        val controller = controller()
        controller.playSeconds(6)

        controller.reset()

        assertEquals(Duration.ZERO, controller.availableSeekBack)
        assertFalse(controller.canSeekBack(1.seconds))
        assertTrue(controller.isLive)
    }

    @Test
    fun `reset clears a previous end-of-stream signal`() {
        val controller = controller()
        controller.buffer.signalEndOfStream()

        controller.reset()

        assertFalse(controller.buffer.isEndOfStream())
    }
}
