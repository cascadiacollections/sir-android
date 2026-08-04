package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMetadataResolverTest {

    private val resolver = StreamMetadataResolver(
        staticTitles = setOf("Will Radio Stream", "SIR"),
        staticArtists = setOf("Live Internet Radio"),
    )

    @Test
    fun `real track metadata replaces previous state`() {
        val update = resolver.resolve(
            StreamMetadata(),
            RawStreamMetadata(title = "Song", artist = "Band", station = "SIR FM"),
        )

        assertEquals("Song", update.metadata.trackTitle)
        assertEquals("Band", update.metadata.artist)
        assertEquals("SIR FM", update.metadata.station)
        assertTrue(update.notifyChanged)
    }

    @Test
    fun `placeholder title is not treated as a track`() {
        val previous = StreamMetadata(trackTitle = "Song", artist = "Band")
        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Will Radio Stream"),
        )

        assertEquals("Song", update.metadata.trackTitle)
        assertFalse(update.notifyChanged)
    }

    @Test
    fun `blank title is not treated as a track`() {
        val previous = StreamMetadata(trackTitle = "Song")
        val update = resolver.resolve(previous, RawStreamMetadata(title = "   "))

        assertEquals("Song", update.metadata.trackTitle)
        assertFalse(update.notifyChanged)
    }

    @Test
    fun `placeholder artist is dropped`() {
        val update = resolver.resolve(
            StreamMetadata(),
            RawStreamMetadata(title = "Song", artist = "Live Internet Radio"),
        )

        assertEquals("Song", update.metadata.trackTitle)
        assertNull(update.metadata.artist)
    }

    @Test
    fun `station change alone still notifies`() {
        val previous = StreamMetadata(trackTitle = "Song", station = "Old")
        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Will Radio Stream", station = "New"),
        )

        assertEquals("New", update.metadata.station)
        assertEquals("Song", update.metadata.trackTitle)
        assertTrue(update.notifyChanged)
    }

    @Test
    fun `blank station keeps the previous station`() {
        val previous = StreamMetadata(station = "Old")
        val update = resolver.resolve(previous, RawStreamMetadata(title = "Song", station = ""))

        assertEquals("Old", update.metadata.station)
    }

    @Test
    fun `repeated identical metadata does not notify`() {
        val previous = StreamMetadata(trackTitle = "Song", artist = "Band", station = "SIR FM")
        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Song", artist = "Band", station = "SIR FM"),
        )

        assertFalse(update.notifyChanged)
    }
}
