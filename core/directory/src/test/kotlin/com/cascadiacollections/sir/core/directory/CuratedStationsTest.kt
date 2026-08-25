package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
        assertEquals(listOf(indie), CuratedStations.matching("INDIE", listOf(indie)))
    }

    @Test
    fun `matching does not depend on the device locale`() {
        // Turkish lowercases 'I' to the dotless 'ı', so a locale-sensitive lowercase()
        // turned "INDIE" into "ındıe" and stopped it matching a station tagged "indie".
        withDefaultLocale("tr-TR") {
            assertEquals(listOf(indie), CuratedStations.matching("INDIE", listOf(indie)))
        }
    }

    @Test
    fun `matching searches tags as well as names`() {
        assertEquals(listOf(indie), CuratedStations.matching("rock", listOf(indie)))
    }

    @Test
    fun `blank text returns every station`() {
        assertEquals(listOf(indie), CuratedStations.matching("   ", listOf(indie)))
    }

    @Test
    fun `non-matching text returns nothing`() {
        assertTrue(CuratedStations.matching("classical", listOf(indie)).isEmpty())
    }
}
