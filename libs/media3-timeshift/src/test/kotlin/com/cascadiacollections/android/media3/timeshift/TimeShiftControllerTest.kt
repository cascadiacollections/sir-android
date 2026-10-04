package com.cascadiacollections.android.media3.timeshift

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

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
            assertFailure {
                TimeShiftController(1024, rate)
            }.isInstanceOf<IllegalArgumentException>()
        }
    }

    @Test
    fun `maxSeekBack reflects capacity at the stream byte rate`() {
        assertThat(controller(capacityBytes = 80).maxSeekBack).isEqualTo(10.seconds)
    }

    @Test
    fun `availableSeekBack grows as audio plays`() {
        val controller = controller()
        assertThat(controller.availableSeekBack).isEqualTo(Duration.ZERO)

        controller.playSeconds(4)
        assertThat(controller.availableSeekBack).isEqualTo(4.seconds)
    }

    @Test
    fun `availableSeekBack is capped by buffer capacity`() {
        val controller = controller(capacityBytes = 80)
        controller.playSeconds(30)
        assertThat(controller.availableSeekBack).isEqualTo(controller.maxSeekBack)
    }

    @Test
    fun `canSeekBack is false until enough audio is buffered`() {
        val controller = controller()
        assertThat(controller.canSeekBack(5.seconds)).isFalse()

        controller.playSeconds(5)
        assertThat(controller.canSeekBack(5.seconds)).isTrue()
    }

    @Test
    fun `seekBack reports failure and leaves playback untouched when under-buffered`() {
        val controller = controller()
        controller.playSeconds(2)

        assertThat(controller.seekBack(5.seconds)).isFalse()
        assertThat(controller.isLive).isTrue()
        assertThat(controller.availableSeekBack).isEqualTo(2.seconds)
    }

    @Test
    fun `seekBack replays the requested duration`() {
        val controller = controller()
        controller.playSeconds(6)
        assertThat(controller.isLive).isTrue()

        assertThat(controller.seekBack(4.seconds)).isTrue()

        assertThat(controller.isLive).isFalse()
        // 4s of audio is queued up to be played a second time.
        assertThat(controller.buffer.available()).isEqualTo(4 * 8)
        assertThat(controller.availableSeekBack).isEqualTo(2.seconds)
    }

    @Test
    fun `goLive returns to the live edge`() {
        val controller = controller()
        controller.playSeconds(6)
        controller.seekBack(4.seconds)

        controller.goLive()

        assertThat(controller.isLive).isTrue()
        assertThat(controller.buffer.available()).isEqualTo(0)
    }

    @Test
    fun `zero and negative durations seek nowhere but still succeed`() {
        val controller = controller()
        controller.playSeconds(3)

        assertThat(controller.seekBack(Duration.ZERO)).isTrue()
        assertThat(controller.seekBack((-5).seconds)).isTrue()
        assertThat(controller.isLive).isTrue()
    }

    @Test
    fun `reset drops buffered audio`() {
        val controller = controller()
        controller.playSeconds(6)

        controller.reset()

        assertThat(controller.availableSeekBack).isEqualTo(Duration.ZERO)
        assertThat(controller.canSeekBack(1.seconds)).isFalse()
        assertThat(controller.isLive).isTrue()
    }

    @Test
    fun `reset clears a previous end-of-stream signal`() {
        val controller = controller()
        controller.buffer.signalEndOfStream()

        controller.reset()

        assertThat(controller.buffer.isEndOfStream()).isFalse()
    }
}
