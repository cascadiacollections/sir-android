package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.model.Station
import org.junit.Test

class StationSearchFiltersTest {

    private fun station(bitrate: Int = 128, country: String = "GB", tags: String = "jazz,soul") =
        Station(id = "s", name = "S", url = "https://s.example", bitrate = bitrate, countryCode = country, tags = tags)

    @Test
    fun `none is inactive and sends nothing`() {
        assertThat(StationSearchFilters.NONE.isActive).isFalse()
        assertThat(StationSearchFilters.NONE.queryParameters()).isEmpty()
    }

    @Test
    fun `values are normalized`() {
        val filters =
            StationSearchFilters(bitrateMinKbps = 0, bitrateMaxKbps = -1, tag = "  ", countryCode = " de ")

        assertThat(filters.bitrateMinKbps).isNull()
        assertThat(filters.bitrateMaxKbps).isNull()
        assertThat(filters.tag).isNull()
        assertThat(filters.countryCode).isEqualTo("DE")
    }

    @Test
    fun `an inverted bitrate range is widened`() {
        val filters = StationSearchFilters(bitrateMinKbps = 192, bitrateMaxKbps = 64)

        assertThat(filters.bitrateMaxKbps).isEqualTo(192)
    }

    @Test
    fun `query parameters use radio-browser names`() {
        val filters = StationSearchFilters(64, 256, "Smooth Jazz", "us")

        assertThat(filters.queryParameters()).containsExactly(
            "bitrateMin" to "64",
            "bitrateMax" to "256",
            "tagList" to "smooth jazz",
            "countrycode" to "US"
        )
        assertThat(filters.queryParameters(includeTag = false))
            .containsExactly("bitrateMin" to "64", "bitrateMax" to "256", "countrycode" to "US")
    }

    @Test
    fun `bitrate bounds are inclusive and unknown bitrate matches`() {
        val filters = StationSearchFilters(bitrateMinKbps = 64, bitrateMaxKbps = 128)

        assertThat(filters.matches(station(bitrate = 64))).isTrue()
        assertThat(filters.matches(station(bitrate = 128))).isTrue()
        assertThat(filters.matches(station(bitrate = 0))).isTrue()
        assertThat(filters.matches(station(bitrate = 32))).isFalse()
        assertThat(filters.matches(station(bitrate = 320))).isFalse()
    }

    @Test
    fun `country matches case-insensitively and missing country matches`() {
        val filters = StationSearchFilters(countryCode = "gb")

        assertThat(filters.matches(station(country = "gb"))).isTrue()
        assertThat(filters.matches(station(country = ""))).isTrue()
        assertThat(filters.matches(station(country = "US"))).isFalse()
    }

    @Test
    fun `tag matches by containment and missing tags match`() {
        val filters = StationSearchFilters(tag = "JAZZ")

        assertThat(filters.matches(station(tags = "smooth jazz"))).isTrue()
        assertThat(filters.matches(station(tags = ""))).isTrue()
        assertThat(filters.matches(station(tags = "rock,metal"))).isFalse()
    }

    @Test
    fun `applyTo keeps order and is a no-op when inactive`() {
        val stations = listOf(station(bitrate = 32), station(bitrate = 128))

        assertThat(StationSearchFilters.NONE.applyTo(stations)).isEqualTo(stations)
        assertThat(StationSearchFilters(bitrateMinKbps = 64).applyTo(stations)).containsExactly(stations[1])
    }

    @Test
    fun `equal filters share a cache key and different ones do not`() {
        assertThat(StationSearchFilters(tag = "jazz ", countryCode = "GB"))
            .isEqualTo(StationSearchFilters(tag = "Jazz", countryCode = "gb"))
        assertThat(StationSearchFilters(tag = "jazz").cacheKey).isEqualTo(StationSearchFilters(tag = "Jazz").cacheKey)
        assertThat(StationSearchFilters(bitrateMaxKbps = 64).cacheKey)
            .isNotEqualTo(StationSearchFilters(bitrateMinKbps = 64).cacheKey)
        assertThat(StationSearchFilters(countryCode = "US").cacheKey).isNotEqualTo(StationSearchFilters.NONE.cacheKey)
    }
}
