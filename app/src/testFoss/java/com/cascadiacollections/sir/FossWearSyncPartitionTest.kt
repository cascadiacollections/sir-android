package com.cascadiacollections.sir

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import org.junit.Test

/**
 * The phone→watch sync rides on the Wearable Data Layer, which is proprietary Play
 * services; the FOSS flavor must ship the inert stub and must not carry the client.
 */
class FossWearSyncPartitionTest {

    @Test
    fun `watch sync is unsupported`() {
        assertThat(WearStationPublisher.isSupported).isFalse()
    }

    @Test
    fun `wearable client is not on the classpath`() {
        assertFailure {
            Class.forName("com.google.android.gms.wearable.Wearable")
        }.isInstanceOf<ClassNotFoundException>()
    }
}
