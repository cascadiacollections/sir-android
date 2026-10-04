package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.cascadiacollections.sir.core.model.Station
import org.junit.Test

class StationNameFormatterTest {

    @Test
    fun `underscores become spaces`() {
        assertThat(StationNameFormatter.normalize("Radio_Paradise_Main_Mix")).isEqualTo("Radio Paradise Main Mix")
    }

    @Test
    fun `bracketed clutter is stripped`() {
        assertThat(StationNameFormatter.normalize("Jazz FM [HD]")).isEqualTo("Jazz FM")
        assertThat(StationNameFormatter.normalize("Jazz FM (128k)")).isEqualTo("Jazz FM")
        assertThat(StationNameFormatter.normalize("(AAC) Jazz FM")).isEqualTo("Jazz FM")
        assertThat(StationNameFormatter.normalize("Jazz [HD] FM (AAC) Live")).isEqualTo("Jazz FM Live")
    }

    @Test
    fun `long parentheticals are kept`() {
        val raw = "Station (an unusually long description that is real text)"
        assertThat(StationNameFormatter.normalize(raw)).isEqualTo(raw)
    }

    @Test
    fun `whitespace is collapsed and trimmed`() {
        assertThat(StationNameFormatter.normalize("  A \t\n  B  ")).isEqualTo("A B")
    }

    @Test
    fun `a name that would become empty keeps its raw form`() {
        assertThat(StationNameFormatter.normalize(" [HD] ")).isEqualTo("[HD]")
        assertThat(StationNameFormatter.normalize("")).isEmpty()
    }

    @Test
    fun `http favicons are upgraded and lose their port`() {
        assertThat(StationNameFormatter.normalizeFavicon("http://img.example:8080/a.png"))
            .isEqualTo("https://img.example/a.png")
        assertThat(StationNameFormatter.normalizeFavicon(" http://img.example/a.png?x=1 "))
            .isEqualTo("https://img.example/a.png?x=1")
    }

    @Test
    fun `https and unparseable favicons are left alone`() {
        assertThat(StationNameFormatter.normalizeFavicon("https://img.example:8443/a.png"))
            .isEqualTo("https://img.example:8443/a.png")
        assertThat(StationNameFormatter.normalizeFavicon("data:image/png;base64,AAA"))
            .isEqualTo("data:image/png;base64,AAA")
    }

    @Test
    fun `blank favicons become null`() {
        assertThat(StationNameFormatter.normalizeFavicon("")).isNull()
        assertThat(StationNameFormatter.normalizeFavicon("   ")).isNull()
        assertThat(StationNameFormatter.normalizeFavicon(null)).isNull()
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

        assertThat(mapped).isEqualTo(raw.copy(name = "Foo Bar", favicon = "https://x.example/f.ico"))
    }
}
