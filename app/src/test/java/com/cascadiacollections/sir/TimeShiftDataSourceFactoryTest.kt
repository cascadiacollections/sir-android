package com.cascadiacollections.sir

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TimeShiftDataSource.Factory].
 */
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
        // ds2 should be a different instance
        assertFalse(ds1 === ds2)
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun `factory lastCreated is null before first createDataSource`() {
        val mockUpstreamFactory = mockk<androidx.media3.datasource.DataSource.Factory>(relaxed = true)
        val buffer = CircularByteBuffer(1024)
        val factory = TimeShiftDataSource.Factory(mockUpstreamFactory, buffer)
        assertTrue(factory.lastCreated == null)
    }
}
