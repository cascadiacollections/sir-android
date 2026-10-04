package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.cascadiacollections.sir.core.model.Station
import java.util.Locale
import org.junit.Test

class CuratedStationsTest {

    private val indie = Station(
        id = "1",
        name = "Indie FM",
        url = "https://example.com/indie",
        tags = "indie,rock"
    )

    /** Runs [body] with [tag] as the default locale, restoring the previous one after. */
    private fun withDefaultLocale(tag: String, body: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(tag))
        try {
            body()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `matching is case insensitive`() {
        assertThat(CuratedStations.matching("INDIE", listOf(indie))).containsExactly(indie)
    }

    @Test
    fun `matching does not depend on the device locale`() {
        // Turkish lowercases 'I' to the dotless 'ı', so a locale-sensitive lowercase()
        // turned "INDIE" into "ındıe" and stopped it matching a station tagged "indie".
        withDefaultLocale("tr-TR") {
            assertThat(CuratedStations.matching("INDIE", listOf(indie))).containsExactly(indie)
        }
    }

    @Test
    fun `matching searches tags as well as names`() {
        assertThat(CuratedStations.matching("rock", listOf(indie))).containsExactly(indie)
    }

    @Test
    fun `blank text returns every station`() {
        assertThat(CuratedStations.matching("   ", listOf(indie))).containsExactly(indie)
    }

    @Test
    fun `non-matching text returns nothing`() {
        assertThat(CuratedStations.matching("classical", listOf(indie))).isEmpty()
    }
}
