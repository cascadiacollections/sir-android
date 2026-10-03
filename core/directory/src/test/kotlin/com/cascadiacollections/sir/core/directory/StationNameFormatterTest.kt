package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationNameFormatterTest {

    @Test
    fun `underscores become spaces`() {
        assertEquals("Radio Paradise Main Mix", StationNameFormatter.normalize("Radio_Paradise_Main_Mix"))
    }

    @Test
    fun `bracketed clutter is stripped`() {
        assertEquals("Jazz FM", StationNameFormatter.normalize("Jazz FM [HD]"))
        assertEquals("Jazz FM", StationNameFormatter.normalize("Jazz FM (128k)"))
        assertEquals("Jazz FM", StationNameFormatter.normalize("(AAC) Jazz FM"))
        assertEquals("Jazz FM Live", StationNameFormatter.normalize("Jazz [HD] FM (AAC) Live"))
    }

    @Test
    fun `long parentheticals are kept`() {
        val raw = "Station (an unusually long description that is real text)"
        assertEquals(raw, StationNameFormatter.normalize(raw))
    }

    @Test
    fun `whitespace is collapsed and trimmed`() {
        assertEquals("A B", StationNameFormatter.normalize("  A \t\n  B  "))
    }

    @Test
    fun `a name that would become empty keeps its raw form`() {
        assertEquals("[HD]", StationNameFormatter.normalize(" [HD] "))
        assertEquals("", StationNameFormatter.normalize(""))
    }

    @Test
    fun `http favicons are upgraded and lose their port`() {
        assertEquals("https://img.example/a.png", StationNameFormatter.normalizeFavicon("http://img.example:8080/a.png"))
        assertEquals("https://img.example/a.png?x=1", StationNameFormatter.normalizeFavicon(" http://img.example/a.png?x=1 "))
    }

    @Test
    fun `https and unparseable favicons are left alone`() {
        assertEquals("https://img.example:8443/a.png", StationNameFormatter.normalizeFavicon("https://img.example:8443/a.png"))
        assertEquals("data:image/png;base64,AAA", StationNameFormatter.normalizeFavicon("data:image/png;base64,AAA"))
    }

    @Test
    fun `blank favicons become null`() {
        assertNull(StationNameFormatter.normalizeFavicon(""))
        assertNull(StationNameFormatter.normalizeFavicon("   "))
        assertNull(StationNameFormatter.normalizeFavicon(null))
    }

    @Test
    fun `mapping only touches name and favicon`() {
        val raw = Station(
            id = "id",
            name = "Foo_Bar (MP3)",
            url = "http://stream.example:8000/live",
            favicon = "http://x.example/f.ico",
            bitrate = 64,
            tags = "a,b"
        )

        val mapped = raw.normalizedFromDirectory()

        assertEquals(raw.copy(name = "Foo Bar", favicon = "https://x.example/f.ico"), mapped)
    }
}
