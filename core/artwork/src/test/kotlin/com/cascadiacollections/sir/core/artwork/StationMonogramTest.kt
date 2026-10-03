package com.cascadiacollections.sir.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationMonogramTest {

    @Test
    fun `initials take the first character of the first two words`() {
        assertEquals("RP", StationMonogram.initials("Radio Paradise"))
        assertEquals("BW", StationMonogram.initials("BBC World Service"))
        assertEquals("K", StationMonogram.initials("KEXP"))
        assertEquals("RC", StationMonogram.initials("radio-canada"))
        assertEquals("1F", StationMonogram.initials("  181.fm  "))
    }

    @Test
    fun `initials handle non latin names`() {
        assertEquals("中", StationMonogram.initials("中国之声"))
        assertEquals("ÉF", StationMonogram.initials("école FM"))
    }

    @Test
    fun `initials fall back when the name has no letters`() {
        assertEquals(StationMonogram.FALLBACK_INITIAL, StationMonogram.initials(""))
        assertEquals(StationMonogram.FALLBACK_INITIAL, StationMonogram.initials("  ~~ "))
    }

    @Test
    fun `hue is deterministic and in range`() {
        val a = StationMonogram.hue("9617a958-0601-11e8-ae97-52543be04c81")
        assertEquals(a, StationMonogram.hue("9617a958-0601-11e8-ae97-52543be04c81"))
        assertTrue(a >= 0f && a < 360f)
        // FNV-1a of the empty string is the offset basis, 2166136261 % 360 = 61.
        assertEquals(61f, StationMonogram.hue(""))
    }

    @Test
    fun `different stations usually get different colours`() {
        val hues = (1..50).map { StationMonogram.hue("station-$it") }.toSet()
        assertTrue(hues.size > 30)
        assertNotEquals(StationMonogram.hue("a"), StationMonogram.hue("b"))
    }

    @Test
    fun `colour follows the id and initials follow the name`() {
        val renamed = StationMonogram.of(id = "uuid-1", name = "New Name")
        val original = StationMonogram.of(id = "uuid-1", name = "Old Name")
        assertEquals(original.hue, renamed.hue)
        assertEquals("NN", renamed.initials)

        val noId = StationMonogram.of(id = "", name = "SIR")
        assertEquals(StationMonogram.hue("SIR"), noId.hue)
    }
}
