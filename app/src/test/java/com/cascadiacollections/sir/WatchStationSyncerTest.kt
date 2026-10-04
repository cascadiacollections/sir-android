package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.WatchStationSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchStationSyncerTest {

    private fun station(id: String) = Station(id = id, name = "Station $id", url = "https://example.com/$id")

    private val selected = MutableStateFlow<Station?>(null)
    private val recents = MutableStateFlow<List<Station>>(emptyList())
    private val published = mutableListOf<String>()

    private fun syncer(publish: suspend (String) -> Unit = { published += it }) =
        WatchStationSyncer(selected, recents, publish, debounceMs = 500)

    @Test
    fun `publishes the initial state once settled`() = runTest {
        selected.value = station("a")
        recents.value = listOf(station("a"))
        syncer().start(backgroundScope)

        advanceTimeBy(501)
        runCurrent()

        val payload = WatchStationSync.decode(published.single())
        assertEquals("a", payload.last!!.id)
        assertEquals(listOf("a"), payload.recents.map { it.id })
    }

    @Test
    fun `coalesces a selection burst into one write`() = runTest {
        syncer().start(backgroundScope)
        advanceTimeBy(501)
        runCurrent()
        published.clear()

        // selectStation: selection and recents land as two emissions.
        selected.value = station("b")
        runCurrent()
        recents.value = listOf(station("b"))
        advanceTimeBy(501)
        runCurrent()

        val payload = WatchStationSync.decode(published.single())
        assertEquals("b", payload.last!!.id)
        assertEquals(listOf("b"), payload.recents.map { it.id })
    }

    @Test
    fun `unchanged state is not re-sent`() = runTest {
        selected.value = station("a")
        syncer().start(backgroundScope)
        advanceTimeBy(501)
        runCurrent()

        selected.value = station("a").copy() // equal value, e.g. an unrelated settings write
        advanceTimeBy(501)
        runCurrent()

        assertEquals(1, published.size)
    }

    @Test
    fun `clearing the selection sends no last station`() = runTest {
        selected.value = station("a")
        syncer().start(backgroundScope)
        advanceTimeBy(501)
        runCurrent()

        selected.value = null
        advanceTimeBy(501)
        runCurrent()

        assertNull(WatchStationSync.decode(published.last()).last)
    }

    @Test
    fun `a failing publish does not stop the syncer`() = runTest {
        var calls = 0
        syncer(publish = {
            calls++
            if (calls == 1) error("Data Layer unavailable")
            published += it
        }).start(backgroundScope)
        advanceTimeBy(501)
        runCurrent()

        selected.value = station("c")
        advanceTimeBy(501)
        runCurrent()

        assertEquals(2, calls)
        assertEquals("c", WatchStationSync.decode(published.single()).last!!.id)
    }
}
