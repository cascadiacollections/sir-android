package com.cascadiacollections.sir

import android.content.Intent
import android.net.Uri
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
        assertThat(StationDeepLink.stationId(view("sir://station/abc-123"))).isEqualTo("abc-123")
    }

    @Test
    fun `ignores other links, actions and missing ids`() {
        assertThat(StationDeepLink.stationId(null)).isNull()
        assertThat(StationDeepLink.stationId(view("https://station/abc"))).isNull()
        assertThat(StationDeepLink.stationId(view("sir://other/abc"))).isNull()
        assertThat(StationDeepLink.stationId(view("sir://station/"))).isNull()
        assertThat(StationDeepLink.stationId(Intent(Intent.ACTION_MAIN, Uri.parse("sir://station/abc")))).isNull()
    }

    @Test
    fun `a phone is not a television`() {
        assertThat(TvActivity.isTelevision(RuntimeEnvironment.getApplication())).isFalse()
    }

    @Test
    @Config(qualifiers = "television")
    fun `a TV is detected, so the phone UI hands over`() {
        assertThat(TvActivity.isTelevision(RuntimeEnvironment.getApplication())).isTrue()
    }

    @Test
    fun `forwarding to the TV keeps the link`() {
        val original = view("sir://station/abc")
        val forwarded = TvActivity.forward(RuntimeEnvironment.getApplication(), original)

        assertThat(forwarded.component?.className).isEqualTo(TvActivity::class.java.name)
        assertThat(StationDeepLink.stationId(forwarded)).isEqualTo("abc")
    }
}
