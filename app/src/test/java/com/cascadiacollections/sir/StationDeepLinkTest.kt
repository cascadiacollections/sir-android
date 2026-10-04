package com.cascadiacollections.sir

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StationDeepLinkTest {

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun `reads the station id from a station link`() {
        assertEquals("abc-123", StationDeepLink.stationId(view("sir://station/abc-123")))
    }

    @Test
    fun `ignores other links, actions and missing ids`() {
        assertNull(StationDeepLink.stationId(null))
        assertNull(StationDeepLink.stationId(view("https://station/abc")))
        assertNull(StationDeepLink.stationId(view("sir://other/abc")))
        assertNull(StationDeepLink.stationId(view("sir://station/")))
        assertNull(StationDeepLink.stationId(Intent(Intent.ACTION_MAIN, Uri.parse("sir://station/abc"))))
    }

    @Test
    fun `a phone is not a television`() {
        assertFalse(TvActivity.isTelevision(RuntimeEnvironment.getApplication()))
    }

    @Test
    @Config(qualifiers = "television")
    fun `a TV is detected, so the phone UI hands over`() {
        assertTrue(TvActivity.isTelevision(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun `forwarding to the TV keeps the link`() {
        val original = view("sir://station/abc")
        val forwarded = TvActivity.forward(RuntimeEnvironment.getApplication(), original)

        assertEquals(TvActivity::class.java.name, forwarded.component?.className)
        assertEquals("abc", StationDeepLink.stationId(forwarded))
    }
}
