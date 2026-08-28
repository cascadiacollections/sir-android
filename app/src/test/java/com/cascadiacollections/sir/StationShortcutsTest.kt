package com.cascadiacollections.sir

import android.content.pm.ShortcutManager
import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StationShortcutsTest {

    private fun station(id: String, name: String = id, url: String = "https://example.com/$id") =
        Station(id = id, name = name, url = url)

    private fun dynamicShortcutIds(): List<String> {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(ShortcutManager::class.java)
        return manager.dynamicShortcuts.map { it.id }
    }

    @Test
    fun `publishes one shortcut per playable station`() {
        val context = RuntimeEnvironment.getApplication()

        StationShortcuts.update(context, listOf(station("a"), station("b")))

        assertEquals(setOf("station-a", "station-b"), dynamicShortcutIds().toSet())
    }

    @Test
    fun `unplayable stations are never published as shortcuts`() {
        val context = RuntimeEnvironment.getApplication()

        StationShortcuts.update(context, listOf(station("a"), Station(id = "b", name = "No URL")))

        assertEquals(listOf("station-a"), dynamicShortcutIds())
    }

    @Test
    fun `updating replaces the previous shortcut set rather than appending`() {
        val context = RuntimeEnvironment.getApplication()
        StationShortcuts.update(context, listOf(station("a"), station("b")))

        StationShortcuts.update(context, listOf(station("c")))

        assertEquals(listOf("station-c"), dynamicShortcutIds())
    }

    @Test
    fun `an empty station list clears all shortcuts`() {
        val context = RuntimeEnvironment.getApplication()
        StationShortcuts.update(context, listOf(station("a")))

        StationShortcuts.update(context, emptyList())

        assertTrue(dynamicShortcutIds().isEmpty())
    }

    @Test
    fun `shortcut count never exceeds what the launcher supports`() {
        val context = RuntimeEnvironment.getApplication()
        val manyStations = (1..50).map { station("s$it") }

        StationShortcuts.update(context, manyStations)

        val maxCount = context.getSystemService(ShortcutManager::class.java).maxShortcutCountPerActivity
        assertTrue(dynamicShortcutIds().size <= maxCount)
    }

    @Test
    fun `a launcher reporting zero shortcut support still clears stale shortcuts`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(ShortcutManager::class.java)
        StationShortcuts.update(context, listOf(station("a")))
        assertEquals(listOf("station-a"), dynamicShortcutIds())

        shadowOf(manager).setMaxShortcutCountPerActivity(0)
        StationShortcuts.update(context, listOf(station("b")))

        assertTrue(dynamicShortcutIds().isEmpty())
    }

    @Test
    fun `shortcuts are ranked in the most-played-first order they're given`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(ShortcutManager::class.java)

        StationShortcuts.update(context, listOf(station("a"), station("b"), station("c")))

        val ranks = manager.dynamicShortcuts.associate { it.id to it.rank }
        assertEquals(0, ranks.getValue("station-a"))
        assertEquals(1, ranks.getValue("station-b"))
        assertEquals(2, ranks.getValue("station-c"))
    }
}
