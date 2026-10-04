package com.cascadiacollections.sir.core.artwork

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import org.junit.Test

class StationMonogramTest {

    @Test
    fun `initials take the first character of the first two words`() {
        assertThat(StationMonogram.initials("Radio Paradise")).isEqualTo("RP")
        assertThat(StationMonogram.initials("BBC World Service")).isEqualTo("BW")
        assertThat(StationMonogram.initials("KEXP")).isEqualTo("K")
        assertThat(StationMonogram.initials("radio-canada")).isEqualTo("RC")
        assertThat(StationMonogram.initials("  181.fm  ")).isEqualTo("1F")
    }

    @Test
    fun `initials handle non latin names`() {
        assertThat(StationMonogram.initials("中国之声")).isEqualTo("中")
        assertThat(StationMonogram.initials("école FM")).isEqualTo("ÉF")
    }

    @Test
    fun `initials fall back when the name has no letters`() {
        assertThat(StationMonogram.initials("")).isEqualTo(StationMonogram.FALLBACK_INITIAL)
        assertThat(StationMonogram.initials("  ~~ ")).isEqualTo(StationMonogram.FALLBACK_INITIAL)
    }

    @Test
    fun `hue is deterministic and in range`() {
        val a = StationMonogram.hue("9617a958-0601-11e8-ae97-52543be04c81")
        assertThat(StationMonogram.hue("9617a958-0601-11e8-ae97-52543be04c81")).isEqualTo(a)
        assertThat(a).isGreaterThanOrEqualTo(0f)
        assertThat(a).isLessThan(360f)
        // FNV-1a of the empty string is the offset basis, 2166136261 % 360 = 61.
        assertThat(StationMonogram.hue("")).isEqualTo(61f)
    }

    @Test
    fun `different stations usually get different colours`() {
        val hues = (1..50).map { StationMonogram.hue("station-$it") }.toSet()
        assertThat(hues.size).isGreaterThan(30)
        assertThat(StationMonogram.hue("b")).isNotEqualTo(StationMonogram.hue("a"))
    }

    @Test
    fun `colour follows the id and initials follow the name`() {
        val renamed = StationMonogram.of(id = "uuid-1", name = "New Name")
        val original = StationMonogram.of(id = "uuid-1", name = "Old Name")
        assertThat(renamed.hue).isEqualTo(original.hue)
        assertThat(renamed.initials).isEqualTo("NN")

        val noId = StationMonogram.of(id = "", name = "SIR")
        assertThat(noId.hue).isEqualTo(StationMonogram.hue("SIR"))
    }
}
