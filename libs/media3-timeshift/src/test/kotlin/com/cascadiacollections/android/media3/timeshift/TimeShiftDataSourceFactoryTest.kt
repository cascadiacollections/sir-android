package com.cascadiacollections.android.media3.timeshift

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class TimeShiftDataSourceFactoryTest {

    private fun controller() = TimeShiftController(capacityBytes = 1024, bytesPerSecond = 8)

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `factory creates TimeShiftDataSource instances`() {
        val mockUpstreamFactory =
            mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val factory = TimeShiftDataSource.Factory(mockUpstreamFactory, controller())
        assertThat(factory.createDataSource()).isNotNull()
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `each call creates a distinct data source`() {
        val mockUpstreamFactory =
            mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val factory = TimeShiftDataSource.Factory(mockUpstreamFactory, controller())

        val ds1 = factory.createDataSource()
        val ds2 = factory.createDataSource()
        assertThat(ds1).isNotSameInstanceAs(ds2)
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `data sources created by one factory share the controller buffer`() {
        val mockUpstreamFactory =
            mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val controller = controller()
        val factory = TimeShiftDataSource.Factory(mockUpstreamFactory, controller)

        factory.createDataSource()
        val second = factory.createDataSource()

        // Bytes written through the shared buffer are visible to any data source's read,
        // which is what lets the controller outlive individual sources.
        controller.buffer.write(byteArrayOf(1, 2, 3), 0, 3)
        val dst = ByteArray(3)
        assertThat(second.read(dst, 0, 3)).isEqualTo(3)
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `factory uses custom thread name and chunk size`() {
        val mockUpstreamFactory =
            mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val factory = TimeShiftDataSource.Factory(
            mockUpstreamFactory,
            controller(),
            threadName = "MyApp-TimeShift",
            chunkSize = 4096
        )
        assertThat(factory.createDataSource()).isNotNull()
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `response headers pass through so ExoPlayer can strip ICY metadata`() {
        val headers = mapOf("icy-metaint" to listOf("16000"), "icy-br" to listOf("256"))
        val upstream = mockk<androidx.media3.datasource.DataSource>(relaxed = true) {
            every { responseHeaders } returns headers
        }
        val source = TimeShiftDataSource(upstream, controller())

        assertThat(source.responseHeaders).isEqualTo(headers)
    }

    @Test
    fun `DEFAULT_CHUNK_SIZE is 8192`() {
        assertThat(TimeShiftDataSource.DEFAULT_CHUNK_SIZE).isEqualTo(8192)
    }
}
