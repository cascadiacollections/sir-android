package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationSearchFiltersTest {

    private fun station(bitrate: Int = 128, country: String = "GB", tags: String = "jazz,soul") =
        Station(id = "s", name = "S", url = "https://s.example", bitrate = bitrate, countryCode = country, tags = tags)

    @Test
    fun `none is inactive and sends nothing`() {
        assertFalse(StationSearchFilters.NONE.isActive)
        assertTrue(StationSearchFilters.NONE.queryParameters().isEmpty())
    }

    @Test
    fun `values are normalized`() {
        val filters = StationSearchFilters(bitrateMinKbps = 0, bitrateMaxKbps = -1, tag = "  ", countryCode = " de ")

        assertNull(filters.bitrateMinKbps)
        assertNull(filters.bitrateMaxKbps)
        assertNull(filters.tag)
        assertEquals("DE", filters.countryCode)
    }

    @Test
    fun `an inverted bitrate range is widened`() {
        val filters = StationSearchFilters(bitrateMinKbps = 192, bitrateMaxKbps = 64)

        assertEquals(192, filters.bitrateMaxKbps)
    }

    @Test
    fun `query parameters use radio-browser names`() {
        val filters = StationSearchFilters(64, 256, "Smooth Jazz", "us")

        assertEquals(
            listOf("bitrateMin" to "64", "bitrateMax" to "256", "tagList" to "smooth jazz", "countrycode" to "US"),
            filters.queryParameters()
        )
        assertEquals(
            listOf("bitrateMin" to "64", "bitrateMax" to "256", "countrycode" to "US"),
            filters.queryParameters(includeTag = false)
        )
    }

    @Test
    fun `bitrate bounds are inclusive and unknown bitrate matches`() {
        val filters = StationSearchFilters(bitrateMinKbps = 64, bitrateMaxKbps = 128)

        assertTrue(filters.matches(station(bitrate = 64)))
        assertTrue(filters.matches(station(bitrate = 128)))
        assertTrue(filters.matches(station(bitrate = 0)))
        assertFalse(filters.matches(station(bitrate = 32)))
        assertFalse(filters.matches(station(bitrate = 320)))
    }

    @Test
    fun `country matches case-insensitively and missing country matches`() {
        val filters = StationSearchFilters(countryCode = "gb")

        assertTrue(filters.matches(station(country = "gb")))
        assertTrue(filters.matches(station(country = "")))
        assertFalse(filters.matches(station(country = "US")))
    }

    @Test
    fun `tag matches by containment and missing tags match`() {
        val filters = StationSearchFilters(tag = "JAZZ")

        assertTrue(filters.matches(station(tags = "smooth jazz")))
        assertTrue(filters.matches(station(tags = "")))
        assertFalse(filters.matches(station(tags = "rock,metal")))
    }

    @Test
    fun `applyTo keeps order and is a no-op when inactive`() {
        val stations = listOf(station(bitrate = 32), station(bitrate = 128))

        assertEquals(stations, StationSearchFilters.NONE.applyTo(stations))
        assertEquals(listOf(stations[1]), StationSearchFilters(bitrateMinKbps = 64).applyTo(stations))
    }

    @Test
    fun `equal filters share a cache key and different ones do not`() {
        assertEquals(StationSearchFilters(tag = "Jazz", countryCode = "gb"), StationSearchFilters(tag = "jazz ", countryCode = "GB"))
        assertEquals(
            StationSearchFilters(tag = "Jazz").cacheKey,
            StationSearchFilters(tag = "jazz").cacheKey
        )
        assertNotEquals(StationSearchFilters(bitrateMinKbps = 64).cacheKey, StationSearchFilters(bitrateMaxKbps = 64).cacheKey)
        assertNotEquals(StationSearchFilters.NONE.cacheKey, StationSearchFilters(countryCode = "US").cacheKey)
    }
}
