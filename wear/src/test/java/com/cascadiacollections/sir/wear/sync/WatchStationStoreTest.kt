package com.cascadiacollections.sir.wear.sync

import android.content.Context
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.WatchStationPayload
import com.cascadiacollections.sir.core.model.WatchStationSync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchStationStoreTest {

    private lateinit var store: WatchStationStore

    private fun station(id: String) = Station(id = id, name = "Station $id", url = "https://example.com/$id")

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("watch_stations", Context.MODE_PRIVATE).edit().clear().commit()
        store = WatchStationStore.from(context)
    }

    @Test
    fun `empty store loads an empty payload`() {
        assertEquals(WatchStationPayload(), store.load())
    }

    @Test
    fun `saved payload loads back`() {
        val payload = WatchStationPayload(last = station("a"), recents = listOf(station("a"), station("b")))

        store.save(WatchStationSync.encode(payload))

        assertEquals(payload, store.load())
        // A fresh instance over the same file sees it too (tile/complication read this way).
        assertEquals(payload, WatchStationStore.from(RuntimeEnvironment.getApplication()).load())
    }

    @Test
    fun `unreadable payload is stored as empty`() {
        store.save(WatchStationSync.encode(WatchStationPayload(last = station("a"))))

        store.save("{not json")

        assertNull(store.load().last)
    }

    @Test
    fun `clear forgets the payload`() {
        store.save(WatchStationSync.encode(WatchStationPayload(last = station("a"))))
        store.clear()
        assertEquals(WatchStationPayload(), store.load())
    }

    @Test
    fun `payloads emits the current value then changes`() = runBlocking {
        store.save(WatchStationSync.encode(WatchStationPayload(last = station("a"))))
        assertEquals("a", store.payloads.first().last!!.id)

        val collected = async { withTimeout(5_000) { store.payloads.take(2).toList() } }
        yield()
        // Robolectric runs prefs listeners on the main looper; give the collector a moment.
        repeat(10) { yield() }
        store.save(WatchStationSync.encode(WatchStationPayload(last = station("b"))))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(listOf("a", "b"), collected.await().map { it.last!!.id })
    }
}
