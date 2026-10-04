package com.cascadiacollections.android.media3.timeshift

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.mockk
import org.junit.Test

@UnstableApi
class TimeShiftDataSourceTest {

    /** Serves [bytes] once, reporting [length] from open (the HTTP Content-Length, or unset). */
    private class FakeUpstream(private val bytes: ByteArray, private val length: Long) : DataSource {
        private var position = 0
        var closed = false

        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long = length
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == bytes.size) return C.RESULT_END_OF_INPUT
            val count = length.coerceAtMost(bytes.size - position)
            System.arraycopy(bytes, position, buffer, offset, count)
            position += count
            return count
        }

        override fun getUri(): Uri? = null
        override fun close() {
            closed = true
        }
    }

    private val spec = DataSpec(mockk<Uri>(relaxed = true))
    private val payload = byteArrayOf(1, 2, 3, 4, 5)

    private fun readAll(source: DataSource): List<Byte> {
        val out = mutableListOf<Byte>()
        val chunk = ByteArray(2)
        while (true) {
            val read = source.read(chunk, 0, chunk.size)
            if (read == C.RESULT_END_OF_INPUT) return out
            repeat(read) { out += chunk[it] }
        }
    }

    @Test
    fun `a finite resource passes through with its length, bypassing the buffer`() {
        val controller = TimeShiftController(capacityBytes = 1024, bytesPerSecond = 8)
        val upstream = FakeUpstream(payload, length = payload.size.toLong())
        val source = TimeShiftDataSource(upstream, controller)

        // The length reaching the player is what gives a newscast file a duration, and so
        // tells its end apart from a live stream dropping.
        assertThat(source.open(spec)).isEqualTo(payload.size.toLong())
        assertThat(readAll(source)).isEqualTo(payload.toList())
        assertThat(controller.buffer.available()).isEqualTo(0)

        source.close()
        assertThat(upstream.closed).isTrue()
    }

    @Test
    fun `a stream of unknown length reads through the time-shift buffer`() {
        val controller = TimeShiftController(capacityBytes = 1024, bytesPerSecond = 8)
        val source = TimeShiftDataSource(FakeUpstream(payload, length = C.LENGTH_UNSET.toLong()), controller)

        assertThat(source.open(spec)).isEqualTo(C.LENGTH_UNSET.toLong())
        // The reader thread fills the buffer, then marks the end so this drains and stops.
        assertThat(readAll(source)).isEqualTo(payload.toList())
        source.close()
    }

    @Test
    fun `a live source opened after a finite one buffers again`() {
        val controller = TimeShiftController(capacityBytes = 1024, bytesPerSecond = 8)
        val finite = TimeShiftDataSource(FakeUpstream(payload, length = payload.size.toLong()), controller)
        finite.open(spec)
        readAll(finite)
        finite.close()

        val live = TimeShiftDataSource(FakeUpstream(byteArrayOf(9, 8), length = C.LENGTH_UNSET.toLong()), controller)
        assertThat(live.open(spec)).isEqualTo(C.LENGTH_UNSET.toLong())
        assertThat(readAll(live)).isEqualTo(listOf<Byte>(9, 8))
        live.close()
    }
}
