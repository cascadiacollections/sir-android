package com.cascadiacollections.sir

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The phone→watch sync rides on the Wearable Data Layer, which is proprietary Play
 * services; the FOSS flavor must ship the inert stub and must not carry the client.
 */
class FossWearSyncPartitionTest {

    @Test
    fun `watch sync is unsupported`() {
        assertFalse(WearStationPublisher.isSupported)
    }

    @Test
    fun `wearable client is not on the classpath`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.google.android.gms.wearable.Wearable")
        }
    }
}
