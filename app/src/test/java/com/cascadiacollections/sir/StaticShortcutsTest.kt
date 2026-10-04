package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
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
                            action = parser.getAttributeValue(ANDROID_NS, "action")
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
        assertThat(entries().count { it.tag == "shortcut" })
            .isEqualTo(StationShortcuts.STATIC_SHORTCUT_COUNT)
    }

    @Test
    fun `whats playing and favorite shortcuts target the headless activities`() {
        val shortcuts = entries().filter { it.tag == "shortcut" }.associateBy { it.name }

        assertThat(shortcuts.getValue("whats_playing").targetClass)
            .isEqualTo(NowPlayingAnnounceActivity::class.java.name)
        assertThat(shortcuts.getValue("whats_playing").action)
            .isEqualTo(NowPlayingAnnounceActivity.ACTION_WHATS_PLAYING)
        assertThat(shortcuts.getValue("favorite_current").targetClass)
            .isEqualTo(FavoriteCurrentStationActivity::class.java.name)
        assertThat(shortcuts.getValue("favorite_current").action)
            .isEqualTo(FavoriteCurrentStationActivity.ACTION_FAVORITE_CURRENT)
    }

    @Test
    fun `assistant capabilities cover play, whats playing and favorite`() {
        val capabilities = entries().filter { it.tag == "capability" }.associateBy { it.name }

        assertThat(capabilities.keys).contains("actions.intent.PLAY_MEDIA")
        assertThat(capabilities.getValue("custom.actions.intent.WHATS_PLAYING").targetClass)
            .isEqualTo(NowPlayingAnnounceActivity::class.java.name)
        assertThat(capabilities.getValue("custom.actions.intent.FAVORITE_CURRENT_STATION").targetClass)
            .isEqualTo(FavoriteCurrentStationActivity::class.java.name)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
