package com.cascadiacollections.sir

import android.content.ClipboardManager
import android.content.pm.ShortcutManager
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.model.Station
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

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

        assertThat(dynamicShortcutIds().toSet()).containsOnly("station-a", "station-b")
    }

    @Test
    fun `unplayable stations are never published as shortcuts`() {
        val context = RuntimeEnvironment.getApplication()

        StationShortcuts.update(context, listOf(station("a"), Station(id = "b", name = "No URL")))

        assertThat(dynamicShortcutIds()).containsExactly("station-a")
    }

    @Test
    fun `updating replaces the previous shortcut set rather than appending`() {
        val context = RuntimeEnvironment.getApplication()
        StationShortcuts.update(context, listOf(station("a"), station("b")))

        StationShortcuts.update(context, listOf(station("c")))

        assertThat(dynamicShortcutIds()).containsExactly("station-c")
    }

    @Test
    fun `an empty station list clears all shortcuts`() {
        val context = RuntimeEnvironment.getApplication()
        StationShortcuts.update(context, listOf(station("a")))

        StationShortcuts.update(context, emptyList())

        assertThat(dynamicShortcutIds()).isEmpty()
    }

    @Test
    fun `shortcut count never exceeds what the launcher supports`() {
        val context = RuntimeEnvironment.getApplication()
        val manyStations = (1..50).map { station("s$it") }

        StationShortcuts.update(context, manyStations)

        val maxCount = context.getSystemService(ShortcutManager::class.java).maxShortcutCountPerActivity
        assertThat(dynamicShortcutIds().size).isLessThanOrEqualTo(maxCount)
    }

    @Test
    fun `a launcher reporting zero shortcut support still clears stale shortcuts`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(ShortcutManager::class.java)
        StationShortcuts.update(context, listOf(station("a")))
        assertThat(dynamicShortcutIds()).containsExactly("station-a")

        shadowOf(manager).setMaxShortcutCountPerActivity(0)
        StationShortcuts.update(context, listOf(station("b")))

        assertThat(dynamicShortcutIds()).isEmpty()
    }

    @Test
    fun `a station id with reserved uri characters round-trips through the deep link`() {
        val context = RuntimeEnvironment.getApplication()
        val reservedId = "https://example.com/stream:8000/live"

        StationShortcuts.update(context, listOf(station(id = reservedId, name = "Imported Station")))

        val manager = context.getSystemService(ShortcutManager::class.java)
        val shortcut = manager.dynamicShortcuts.single()
        assertThat(shortcut.intent?.data?.lastPathSegment).isEqualTo(reservedId)
    }

    @Test
    fun `stations with a blank name are never published as shortcuts`() {
        val context = RuntimeEnvironment.getApplication()

        StationShortcuts.update(
            context,
            listOf(station("a"), station(id = "b", name = "   "))
        )

        assertThat(dynamicShortcutIds()).containsExactly("station-a")
    }

    @Test
    fun `shortcuts are ranked in the most-played-first order they're given`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(ShortcutManager::class.java)

        StationShortcuts.update(context, listOf(station("a"), station("b"), station("c")))

        val ranks = manager.dynamicShortcuts.associate { it.id to it.rank }
        assertThat(ranks.getValue("station-a")).isEqualTo(0)
        assertThat(ranks.getValue("station-b")).isEqualTo(1)
        assertThat(ranks.getValue("station-c")).isEqualTo(2)
    }

    @Test
    fun `station shortcuts leave room for the static ones`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(ShortcutManager::class.java)
        shadowOf(manager).setMaxShortcutCountPerActivity(5)

        StationShortcuts.update(context, (1..10).map { station("s$it") })

        // 5 slots, 3 taken by "Play", "What's playing?" and "Favorite this station".
        assertThat(manager.dynamicShortcuts.sortedBy { it.rank }.map { it.id })
            .containsExactly("station-s1", "station-s2")
    }

    @Test
    fun `no station shortcuts when the static ones fill the launcher`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(ShortcutManager::class.java)
        shadowOf(manager).setMaxShortcutCountPerActivity(StationShortcuts.STATIC_SHORTCUT_COUNT)

        StationShortcuts.update(context, listOf(station("a")))

        assertThat(dynamicShortcutIds()).isEmpty()
    }

    @Test
    fun `dynamic capacity never goes negative`() {
        assertThat(StationShortcuts.dynamicCapacity(0)).isEqualTo(0)
        assertThat(StationShortcuts.dynamicCapacity(2)).isEqualTo(0)
        assertThat(StationShortcuts.dynamicCapacity(4)).isEqualTo(1)
        assertThat(StationShortcuts.dynamicCapacity(5)).isEqualTo(2)
    }

    @Test
    fun `a pinned shortcut plays the station headlessly, apart from the dynamic ones`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(ShortcutManager::class.java)

        assertThat(StationShortcuts.requestPin(context, station("a", name = "KEXP"))).isTrue()

        val pinned = manager.pinnedShortcuts.single()
        assertThat(pinned.id).isEqualTo("play-a")
        assertThat(pinned.shortLabel.toString()).isEqualTo("KEXP")
        val intent = pinned.intent!!
        assertThat(intent.component?.className).isEqualTo(PlayStationActivity::class.java.name)
        assertThat(intent.action).isEqualTo(PlayStationActivity.ACTION_PLAY_STATION)
        assertThat(intent.data.toString()).isEqualTo("sir://play/a")
        assertThat(dynamicShortcutIds()).isEmpty()
    }

    @Test
    fun `nothing is pinned for a station with nothing to play`() {
        val context = RuntimeEnvironment.getApplication()

        assertThat(StationShortcuts.requestPin(context, Station(id = "b", name = "No URL"))).isFalse()
    }

    @Test
    fun `copying the automation link puts the play link on the clipboard`() {
        val context = RuntimeEnvironment.getApplication()

        AutomationLinks.copy(context, station("a5314180-7573-4b46-aafc-51ed2d5b9e71"))

        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertThat(clip.getItemAt(0).text.toString()).isEqualTo("sir://play/a5314180-7573-4b46-aafc-51ed2d5b9e71")
        // Android 13+ shows its own clipboard confirmation.
        assertThat(ShadowToast.getTextOfLatestToast()).isNull()
    }

    @Test
    @Config(sdk = [32])
    fun `before Android 13 a copy is confirmed with a toast`() {
        AutomationLinks.copy(RuntimeEnvironment.getApplication(), station("a"))

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Automation link copied")
    }
}
