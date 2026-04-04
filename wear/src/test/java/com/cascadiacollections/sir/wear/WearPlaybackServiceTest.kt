package com.cascadiacollections.sir.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPlaybackServiceTest {

    @Test
    fun `stream URL is valid HTTPS`() {
        val field = WearPlaybackService::class.java.getDeclaredField("STREAM_URL")
        field.isAccessible = true
        val url = field.get(null) as String
        assertTrue("Stream URL should use HTTPS", url.startsWith("https://"))
        assertTrue("Stream URL should not be blank", url.isNotBlank())
    }

    @Test
    fun `session ID is not blank`() {
        val field = WearPlaybackService::class.java.getDeclaredField("SESSION_ID")
        field.isAccessible = true
        val id = field.get(null) as String
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `channel ID is not blank`() {
        val field = WearPlaybackService::class.java.getDeclaredField("CHANNEL_ID")
        field.isAccessible = true
        val id = field.get(null) as String
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `notification ID is positive`() {
        val field = WearPlaybackService::class.java.getDeclaredField("NOTIFICATION_ID")
        field.isAccessible = true
        val id = field.getInt(null)
        assertTrue("Notification ID should be positive", id > 0)
    }

    @Test
    fun `stream URL matches app module URL`() {
        val field = WearPlaybackService::class.java.getDeclaredField("STREAM_URL")
        field.isAccessible = true
        val url = field.get(null) as String
        assertEquals(
            "https://broadcast.shoutcheap.com/proxy/willradio/stream",
            url
        )
    }
}
