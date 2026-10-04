package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** `res/xml/shortcuts.xml` against what the code assumes about it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StaticShortcutsTest {

    private data class Entry(val tag: String, val name: String?, val targetClass: String?, val action: String?)

    /** Each `<shortcut>`/`<capability>` with the first `<intent>` inside it. */
    private fun entries(): List<Entry> {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(R.xml.shortcuts)
        val entries = mutableListOf<Entry>()
        var tag: String? = null
        var name: String? = null
        var intentSeen = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "shortcut", "capability" -> {
                        tag = parser.name
                        name = parser.getAttributeValue(ANDROID_NS, "shortcutId")
                            ?: parser.getAttributeValue(ANDROID_NS, "name")
                        intentSeen = false
                    }
                    "intent" -> if (tag != null && !intentSeen) {
                        intentSeen = true
                        entries += Entry(
                            tag = tag,
                            name = name,
                            targetClass = parser.getAttributeValue(ANDROID_NS, "targetClass"),
                            action = parser.getAttributeValue(ANDROID_NS, "action"),
                        )
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "shortcut" || parser.name == "capability") tag = null
            }
        }
        return entries
    }

    @Test
    fun `static shortcut count matches what StationShortcuts reserves`() {
        assertEquals(StationShortcuts.STATIC_SHORTCUT_COUNT, entries().count { it.tag == "shortcut" })
    }

    @Test
    fun `whats playing and favorite shortcuts target the headless activities`() {
        val shortcuts = entries().filter { it.tag == "shortcut" }.associateBy { it.name }

        assertEquals(NowPlayingAnnounceActivity::class.java.name, shortcuts.getValue("whats_playing").targetClass)
        assertEquals(NowPlayingAnnounceActivity.ACTION_WHATS_PLAYING, shortcuts.getValue("whats_playing").action)
        assertEquals(FavoriteCurrentStationActivity::class.java.name, shortcuts.getValue("favorite_current").targetClass)
        assertEquals(
            FavoriteCurrentStationActivity.ACTION_FAVORITE_CURRENT,
            shortcuts.getValue("favorite_current").action,
        )
    }

    @Test
    fun `assistant capabilities cover play, whats playing and favorite`() {
        val capabilities = entries().filter { it.tag == "capability" }.associateBy { it.name }

        assertTrue("actions.intent.PLAY_MEDIA" in capabilities)
        assertEquals(
            NowPlayingAnnounceActivity::class.java.name,
            capabilities.getValue("custom.actions.intent.WHATS_PLAYING").targetClass,
        )
        assertEquals(
            FavoriteCurrentStationActivity::class.java.name,
            capabilities.getValue("custom.actions.intent.FAVORITE_CURRENT_STATION").targetClass,
        )
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
