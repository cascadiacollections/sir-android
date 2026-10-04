package com.cascadiacollections.sir.wear

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import org.junit.Test

class WearPlaybackServiceTest {

    // Top-level private constants compile into the file-level Kt class
    private val fileClass = Class.forName("com.cascadiacollections.sir.wear.WearPlaybackServiceKt")

    @Test
    fun `stream URL is valid HTTPS`() {
        val field = fileClass.getDeclaredField("STREAM_URL")
        field.isAccessible = true
        val url = field.get(null) as String
        assertThat(url, name = "Stream URL should use HTTPS").startsWith("https://")
        assertThat(url.isNotBlank(), name = "Stream URL should not be blank").isTrue()
    }

    @Test
    fun `session ID is not blank`() {
        val field = fileClass.getDeclaredField("SESSION_ID")
        field.isAccessible = true
        val id = field.get(null) as String
        assertThat(id.isNotBlank()).isTrue()
    }

    @Test
    fun `channel ID is not blank`() {
        val field = fileClass.getDeclaredField("CHANNEL_ID")
        field.isAccessible = true
        val id = field.get(null) as String
        assertThat(id.isNotBlank()).isTrue()
    }

    @Test
    fun `notification ID is positive`() {
        val field = fileClass.getDeclaredField("NOTIFICATION_ID")
        field.isAccessible = true
        val id = field.getInt(null)
        assertThat(id, name = "Notification ID should be positive").isGreaterThan(0)
    }

    @Test
    fun `stream URL matches app module URL`() {
        val field = fileClass.getDeclaredField("STREAM_URL")
        field.isAccessible = true
        val url = field.get(null) as String
        assertThat(url).isEqualTo("https://broadcast.shoutcheap.com/proxy/willradio/stream")
    }
}
