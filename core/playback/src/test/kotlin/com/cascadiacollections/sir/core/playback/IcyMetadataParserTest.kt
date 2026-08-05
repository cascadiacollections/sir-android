package com.cascadiacollections.sir.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IcyMetadataParserTest {

    @Test
    fun `plain artist and title are split`() {
        val track = IcyMetadataParser.parseTrack("Black Eyed Peas - Boom Boom Pow")

        assertEquals("Boom Boom Pow", track.title)
        assertEquals("Black Eyed Peas", track.artist)
    }

    @Test
    fun `a title with no separator keeps the whole string`() {
        val track = IcyMetadataParser.parseTrack("  September  ")

        assertEquals("September", track.title)
        assertNull(track.artist)
    }

    @Test
    fun `classic ICY fields are unwrapped`() {
        val track = IcyMetadataParser.parseTrack(
            "StreamTitle='Fleetwood Mac - Dreams';StreamUrl='https://example.org';"
        )

        assertEquals("Dreams", track.title)
        assertEquals("Fleetwood Mac", track.artist)
    }

    @Test
    fun `an apostrophe inside a quoted value does not end it`() {
        val track = IcyMetadataParser.parseTrack("StreamTitle='Journey - Don't Stop Believin'';")

        assertEquals("Don't Stop Believin'", track.title)
        assertEquals("Journey", track.artist)
    }

    @Test
    fun `broadcaster HLS fields are read as already split`() {
        val track = IcyMetadataParser.parseTrack("title=\"Boom Boom Pow\",artist=Black Eyed Peas")

        assertEquals("Boom Boom Pow", track.title)
        assertEquals("Black Eyed Peas", track.artist)
    }

    @Test
    fun `an unquoted value may contain a comma`() {
        val track = IcyMetadataParser.parseTrack("StreamTitle=Earth, Wind & Fire - September;")

        assertEquals("September", track.title)
        assertEquals("Earth, Wind & Fire", track.artist)
    }

    @Test
    fun `Triton cue metadata is read from its text field`() {
        val track = IcyMetadataParser.parseTrack(
            "text=\"Prince - Kiss\" amgTrackId=\"9876543\" length=\"00:03:46\""
        )

        assertEquals("Kiss", track.title)
        assertEquals("Prince", track.artist)
    }

    @Test
    fun `a nested cue block inside a classic ICY field is unwrapped`() {
        // Captured live from WHTZ: a leading empty-artist separator hiding a cue block.
        val track = IcyMetadataParser.parseTrack(
            "StreamTitle=' - text=\"Spot Block End\" amgTrackId=\"9876543\" length=\"00:00:00\"';"
        )

        assertNull(track.title)
        assertNull(track.artist)
    }

    @Test
    fun `an ad cue marker is suppressed rather than shown as a title`() {
        assertNull(IcyMetadataParser.parseTrack("Spot Block Start").title)
    }

    @Test
    fun `cue metadata with no title-bearing key is suppressed`() {
        assertNull(IcyMetadataParser.parseTrack("TrackId=12345,length=00:03:12").title)
    }

    @Test
    fun `undecomposable key-value soup is never displayed`() {
        // Mismatched quoting keeps the tokenizer from decomposing this, so the last-resort
        // guard has to catch it.
        assertNull(IcyMetadataParser.parseTrack("text=\"unterminated - amgTrackId=\"1\"x").title)
    }

    @Test
    fun `an equals sign in a real title is left alone`() {
        val track = IcyMetadataParser.parseTrack("Mariah Carey - E=MC2")

        assertEquals("E=MC2", track.title)
        assertEquals("Mariah Carey", track.artist)
    }

    @Test
    fun `an empty artist half yields a title only`() {
        val track = IcyMetadataParser.parseTrack(" - Orphan Title")

        assertEquals("Orphan Title", track.title)
        assertNull(track.artist)
    }

    @Test
    fun `blank metadata yields nothing`() {
        assertNull(IcyMetadataParser.parseTrack("   ").title)
        assertNull(IcyMetadataParser.parseTrack("").title)
    }

    @Test
    fun `only the first separator splits the pair`() {
        val track = IcyMetadataParser.parseTrack("Simon - Garfunkel - Mrs. Robinson")

        assertEquals("Garfunkel - Mrs. Robinson", track.title)
        assertEquals("Simon", track.artist)
    }
}
