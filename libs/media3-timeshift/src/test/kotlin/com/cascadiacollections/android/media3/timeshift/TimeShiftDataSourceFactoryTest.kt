package com.cascadiacollections.android.media3.timeshift

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeShiftDataSourceFactoryTest {

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `factory creates TimeShiftDataSource instances`() {
        val mockUpstreamFactory = mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val buffer = CircularByteBuffer(1024)
        val factory = TimeShiftDataSource.Factory(mockUpstreamFactory, buffer)
        val ds = factory.createDataSource()
        assertFalse(ds == null)
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `factory lastCreated tracks most recent data source`() {
        val mockUpstreamFactory = mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val buffer = CircularByteBuffer(1024)
        val factory = TimeShiftDataSource.Factory(mockUpstreamFactory, buffer)

        val ds1 = factory.createDataSource()
        assertEquals(ds1, factory.lastCreated)

        val ds2 = factory.createDataSource()
        assertEquals(ds2, factory.lastCreated)
        assertFalse(ds1 === ds2)
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `factory lastCreated is null before first createDataSource`() {
        val mockUpstreamFactory = mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val buffer = CircularByteBuffer(1024)
        val factory = TimeShiftDataSource.Factory(mockUpstreamFactory, buffer)
        assertNull(factory.lastCreated)
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `factory uses custom thread name and chunk size`() {
        val mockUpstreamFactory = mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val buffer = CircularByteBuffer(1024)
        val factory = TimeShiftDataSource.Factory(
            mockUpstreamFactory,
            buffer,
            threadName = "MyApp-TimeShift",
            chunkSize = 4096
        )
        val ds = factory.createDataSource()
        assertEquals(ds, factory.lastCreated)
    }

    @Test
    fun `DEFAULT_CHUNK_SIZE is 8192`() {
        assertEquals(8192, TimeShiftDataSource.DEFAULT_CHUNK_SIZE)
    }
}
