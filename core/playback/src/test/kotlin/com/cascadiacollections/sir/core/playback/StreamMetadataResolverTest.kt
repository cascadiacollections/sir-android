package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class StreamMetadataResolverTest {

    private val resolver = StreamMetadataResolver(
        staticTitles = setOf("Will Radio Stream", "SIR"),
        staticArtists = setOf("Live Internet Radio")
    )

    @Test
    fun `real track metadata replaces previous state`() {
        val update = resolver.resolve(
            StreamMetadata(),
            RawStreamMetadata(title = "Song", artist = "Band", station = "SIR FM")
        )

        assertThat(update.metadata.trackTitle).isEqualTo("Song")
        assertThat(update.metadata.artist).isEqualTo("Band")
        assertThat(update.metadata.station).isEqualTo("SIR FM")
        assertThat(update.notifyChanged).isTrue()
    }

    @Test
    fun `placeholder title is not treated as a track`() {
        val previous = StreamMetadata(trackTitle = "Song", artist = "Band")
        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Will Radio Stream")
        )

        assertThat(update.metadata.trackTitle).isEqualTo("Song")
        assertThat(update.notifyChanged).isFalse()
    }

    @Test
    fun `blank title is not treated as a track`() {
        val previous = StreamMetadata(trackTitle = "Song")
        val update = resolver.resolve(previous, RawStreamMetadata(title = "   "))

        assertThat(update.metadata.trackTitle).isEqualTo("Song")
        assertThat(update.notifyChanged).isFalse()
    }

    @Test
    fun `placeholder artist is dropped`() {
        val update = resolver.resolve(
            StreamMetadata(),
            RawStreamMetadata(title = "Song", artist = "Live Internet Radio")
        )

        assertThat(update.metadata.trackTitle).isEqualTo("Song")
        assertThat(update.metadata.artist).isNull()
    }

    @Test
    fun `station change alone still notifies`() {
        val previous = StreamMetadata(trackTitle = "Song", station = "Old")
        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Will Radio Stream", station = "New")
        )

        assertThat(update.metadata.station).isEqualTo("New")
        assertThat(update.metadata.trackTitle).isEqualTo("Song")
        assertThat(update.notifyChanged).isTrue()
    }

    @Test
    fun `blank station keeps the previous station`() {
        val previous = StreamMetadata(station = "Old")
        val update = resolver.resolve(previous, RawStreamMetadata(title = "Song", station = ""))

        assertThat(update.metadata.station).isEqualTo("Old")
    }

    @Test
    fun `repeated identical metadata does not notify`() {
        val previous = StreamMetadata(trackTitle = "Song", artist = "Band", station = "SIR FM")
        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Song", artist = "Band", station = "SIR FM")
        )

        assertThat(update.notifyChanged).isFalse()
    }

    @Test
    fun `a blank artist is treated as unknown rather than overwriting a known one`() {
        val previous = StreamMetadata(trackTitle = "Old", artist = "Band", station = "SIR FM")

        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Song", artist = "   ", station = "SIR FM")
        )

        // Keeping "" replaced a real artist with an empty subtitle in the notification.
        assertThat(update.metadata.artist).isNull()
        assertThat(update.metadata.trackTitle).isEqualTo("Song")
    }

    @Test
    fun `a repeated blank artist does not keep reporting a change`() {
        val raw = RawStreamMetadata(title = "Song", artist = "", station = "SIR FM")
        val first = resolver.resolve(StreamMetadata(), raw)

        val second = resolver.resolve(first.metadata, raw)

        assertThat(first.notifyChanged).isTrue()
        assertThat(second.notifyChanged).isFalse()
    }

    @Test
    fun `a combined ICY title is split into artist and track`() {
        // What Media3 actually hands us: the raw StreamTitle, artist and all.
        val update = resolver.resolve(
            StreamMetadata(),
            RawStreamMetadata(title = "Fleetwood Mac - Dreams", station = "SIR FM")
        )

        assertThat(update.metadata.trackTitle).isEqualTo("Dreams")
        assertThat(update.metadata.artist).isEqualTo("Fleetwood Mac")
        assertThat(update.notifyChanged).isTrue()
    }

    @Test
    fun `a parsed artist wins over the artist the player reported`() {
        // The player's artist field carries our own MediaItem metadata for most streams,
        // so the one parsed out of the stream title is the better answer.
        val update = resolver.resolve(
            StreamMetadata(),
            RawStreamMetadata(title = "Prince - Kiss", artist = "Live Internet Radio")
        )

        assertThat(update.metadata.trackTitle).isEqualTo("Kiss")
        assertThat(update.metadata.artist).isEqualTo("Prince")
    }

    @Test
    fun `an ad break cue keeps the previous track on screen`() {
        val previous = StreamMetadata(trackTitle = "Dreams", artist = "Fleetwood Mac")

        val update = resolver.resolve(previous, RawStreamMetadata(title = "Spot Block End"))

        assertThat(update.metadata.trackTitle).isEqualTo("Dreams")
        assertThat(update.metadata.artist).isEqualTo("Fleetwood Mac")
        assertThat(update.notifyChanged).isFalse()
    }

    @Test
    fun `junk that survives parsing is filtered out`() {
        val previous = StreamMetadata(trackTitle = "Dreams")

        val update = resolver.resolve(previous, RawStreamMetadata(title = "https://sir.example"))

        assertThat(update.metadata.trackTitle).isEqualTo("Dreams")
        assertThat(update.notifyChanged).isFalse()
    }

    @Test
    fun `the station plugging itself is not treated as a track`() {
        val previous = StreamMetadata(trackTitle = "Dreams")

        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "KEXP 90.3 FM"),
            stationName = "KEXP903FM"
        )

        assertThat(update.metadata.trackTitle).isEqualTo("Dreams")
        assertThat(update.notifyChanged).isFalse()
    }

    @Test
    fun `a placeholder hiding behind an artist half is still rejected`() {
        val previous = StreamMetadata(trackTitle = "Dreams")

        val update = resolver.resolve(previous, RawStreamMetadata(title = "SIR FM - Will Radio Stream"))

        assertThat(update.metadata.trackTitle).isEqualTo("Dreams")
        assertThat(update.notifyChanged).isFalse()
    }

    @Test
    fun `a station change is still reported when the title is junk`() {
        val previous = StreamMetadata(trackTitle = "Dreams", station = "Old")

        val update = resolver.resolve(
            previous,
            RawStreamMetadata(title = "Spot Block End", station = "New")
        )

        assertThat(update.metadata.station).isEqualTo("New")
        assertThat(update.metadata.trackTitle).isEqualTo("Dreams")
        assertThat(update.notifyChanged).isTrue()
    }
}
