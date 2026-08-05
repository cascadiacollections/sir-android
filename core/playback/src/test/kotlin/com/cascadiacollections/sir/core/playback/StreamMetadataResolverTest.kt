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

    @Test
    fun `a blank artist is treated as unknown rather than overwriting a known one`() {
        val previous = StreamMetadata(trackTitle = "Old", artist = "Band", station = "SIR FM")

        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Song", artist = "   ", station = "SIR FM"),
        )

        // Keeping "" replaced a real artist with an empty subtitle in the notification.
        assertNull(update.metadata.artist)
        assertEquals("Song", update.metadata.trackTitle)
    }

    @Test
    fun `a repeated blank artist does not keep reporting a change`() {
        val raw = RawStreamMetadata(title = "Song", artist = "", station = "SIR FM")
        val first = resolver.resolve(StreamMetadata(), raw)

        val second = resolver.resolve(first.metadata, raw)

        assertTrue(first.notifyChanged)
        assertFalse(second.notifyChanged)
    }

    @Test
    fun `a combined ICY title is split into artist and track`() {
        // What Media3 actually hands us: the raw StreamTitle, artist and all.
        val update = resolver.resolve(
            StreamMetadata(),
            RawStreamMetadata(title = "Fleetwood Mac - Dreams", station = "SIR FM"),
        )

        assertEquals("Dreams", update.metadata.trackTitle)
        assertEquals("Fleetwood Mac", update.metadata.artist)
        assertTrue(update.notifyChanged)
    }

    @Test
    fun `a parsed artist wins over the artist the player reported`() {
        // The player's artist field carries our own MediaItem metadata for most streams,
        // so the one parsed out of the stream title is the better answer.
        val update = resolver.resolve(
            StreamMetadata(),
            RawStreamMetadata(title = "Prince - Kiss", artist = "Live Internet Radio"),
        )

        assertEquals("Kiss", update.metadata.trackTitle)
        assertEquals("Prince", update.metadata.artist)
    }

    @Test
    fun `an ad break cue keeps the previous track on screen`() {
        val previous = StreamMetadata(trackTitle = "Dreams", artist = "Fleetwood Mac")

        val update = resolver.resolve(previous, RawStreamMetadata(title = "Spot Block End"))

        assertEquals("Dreams", update.metadata.trackTitle)
        assertEquals("Fleetwood Mac", update.metadata.artist)
        assertFalse(update.notifyChanged)
    }

    @Test
    fun `junk that survives parsing is filtered out`() {
        val previous = StreamMetadata(trackTitle = "Dreams")

        val update = resolver.resolve(previous, RawStreamMetadata(title = "https://sir.example"))

        assertEquals("Dreams", update.metadata.trackTitle)
        assertFalse(update.notifyChanged)
    }

    @Test
    fun `the station plugging itself is not treated as a track`() {
        val previous = StreamMetadata(trackTitle = "Dreams")

        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "KEXP 90.3 FM"),
            stationName = "KEXP903FM",
        )

        assertEquals("Dreams", update.metadata.trackTitle)
        assertFalse(update.notifyChanged)
    }

    @Test
    fun `a placeholder hiding behind an artist half is still rejected`() {
        val previous = StreamMetadata(trackTitle = "Dreams")

        val update = resolver.resolve(previous, RawStreamMetadata(title = "SIR FM - Will Radio Stream"))

        assertEquals("Dreams", update.metadata.trackTitle)
        assertFalse(update.notifyChanged)
    }

    @Test
    fun `a station change is still reported when the title is junk`() {
        val previous = StreamMetadata(trackTitle = "Dreams", station = "Old")

        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Spot Block End", station = "New"),
        )

        assertEquals("New", update.metadata.station)
        assertEquals("Dreams", update.metadata.trackTitle)
        assertTrue(update.notifyChanged)
    }
}
