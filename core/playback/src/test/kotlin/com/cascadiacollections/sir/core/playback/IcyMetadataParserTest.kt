package com.cascadiacollections.sir.core.playback

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class IcyMetadataParserTest {

    @Test
    fun `plain artist and title are split`() {
        val track = IcyMetadataParser.parseTrack("Black Eyed Peas - Boom Boom Pow")

        assertThat(track.title).isEqualTo("Boom Boom Pow")
        assertThat(track.artist).isEqualTo("Black Eyed Peas")
    }

    @Test
    fun `a title with no separator keeps the whole string`() {
        val track = IcyMetadataParser.parseTrack("  September  ")

        assertThat(track.title).isEqualTo("September")
        assertThat(track.artist).isNull()
    }

    @Test
    fun `classic ICY fields are unwrapped`() {
        val track = IcyMetadataParser.parseTrack(
            "StreamTitle='Fleetwood Mac - Dreams';StreamUrl='https://example.org';"
        )

        assertThat(track.title).isEqualTo("Dreams")
        assertThat(track.artist).isEqualTo("Fleetwood Mac")
    }

    @Test
    fun `an apostrophe inside a quoted value does not end it`() {
        val track = IcyMetadataParser.parseTrack("StreamTitle='Journey - Don't Stop Believin'';")

        assertThat(track.title).isEqualTo("Don't Stop Believin'")
        assertThat(track.artist).isEqualTo("Journey")
    }

    @Test
    fun `broadcaster HLS fields are read as already split`() {
        val track = IcyMetadataParser.parseTrack("title=\"Boom Boom Pow\",artist=Black Eyed Peas")

        assertThat(track.title).isEqualTo("Boom Boom Pow")
        assertThat(track.artist).isEqualTo("Black Eyed Peas")
    }

    @Test
    fun `an unquoted value may contain a comma`() {
        val track = IcyMetadataParser.parseTrack("StreamTitle=Earth, Wind & Fire - September;")

        assertThat(track.title).isEqualTo("September")
        assertThat(track.artist).isEqualTo("Earth, Wind & Fire")
    }

    @Test
    fun `Triton cue metadata is read from its text field`() {
        val track = IcyMetadataParser.parseTrack(
            "text=\"Prince - Kiss\" amgTrackId=\"9876543\" length=\"00:03:46\""
        )

        assertThat(track.title).isEqualTo("Kiss")
        assertThat(track.artist).isEqualTo("Prince")
    }

    @Test
    fun `a nested cue block inside a classic ICY field is unwrapped`() {
        // Captured live from WHTZ: a leading empty-artist separator hiding a cue block.
        val track = IcyMetadataParser.parseTrack(
            "StreamTitle=' - text=\"Spot Block End\" amgTrackId=\"9876543\" length=\"00:00:00\"';"
        )

        assertThat(track.title).isNull()
        assertThat(track.artist).isNull()
    }

    @Test
    fun `an ad cue marker is suppressed rather than shown as a title`() {
        assertThat(IcyMetadataParser.parseTrack("Spot Block Start").title).isNull()
    }

    @Test
    fun `cue metadata with no title-bearing key is suppressed`() {
        assertThat(IcyMetadataParser.parseTrack("TrackId=12345,length=00:03:12").title).isNull()
    }

    @Test
    fun `undecomposable key-value soup is never displayed`() {
        // Mismatched quoting keeps the tokenizer from decomposing this, so the last-resort
        // guard has to catch it.
        assertThat(IcyMetadataParser.parseTrack("text=\"unterminated - amgTrackId=\"1\"x").title).isNull()
    }

    @Test
    fun `an equals sign in a real title is left alone`() {
        val track = IcyMetadataParser.parseTrack("Mariah Carey - E=MC2")

        assertThat(track.title).isEqualTo("E=MC2")
        assertThat(track.artist).isEqualTo("Mariah Carey")
    }

    @Test
    fun `an empty artist half yields a title only`() {
        val track = IcyMetadataParser.parseTrack(" - Orphan Title")

        assertThat(track.title).isEqualTo("Orphan Title")
        assertThat(track.artist).isNull()
    }

    @Test
    fun `blank metadata yields nothing`() {
        assertThat(IcyMetadataParser.parseTrack("   ").title).isNull()
        assertThat(IcyMetadataParser.parseTrack("").title).isNull()
    }

    @Test
    fun `only the first separator splits the pair`() {
        val track = IcyMetadataParser.parseTrack("Simon - Garfunkel - Mrs. Robinson")

        assertThat(track.title).isEqualTo("Garfunkel - Mrs. Robinson")
        assertThat(track.artist).isEqualTo("Simon")
    }
}
