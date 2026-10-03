package com.cascadiacollections.sir.core.directory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StationIdsAndTagTest {

    @Test
    fun `radio-browser uuids are recognised`() {
        assertTrue(StationIds.isRadioBrowserUuid("96062a7b-0601-11e8-ae97-52543be04c81"))
        assertTrue(StationIds.isRadioBrowserUuid("96062A7B-0601-11E8-AE97-52543BE04C81"))
    }

    @Test
    fun `bundled and imported ids are not uuids`() {
        listOf(
            "sir-default",
            "curated-subcity",
            "imported:https://example.com/stream",
            "",
            "96062a7b060111e8ae9752543be04c81",
            " 96062a7b-0601-11e8-ae97-52543be04c81",
            "96062a7b-0601-11e8-ae97-52543be04c8z"
        ).forEach { assertFalse(it, StationIds.isRadioBrowserUuid(it)) }
    }

    @Test
    fun `tag display names are capitalized per word without locale effects`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            assertEquals("Indie", Tag("indie").displayName)
            assertEquals("Hip Hop", Tag("hip hop").displayName)
            assertEquals("80s", Tag("80s").displayName)
            assertEquals("Classical", Tag("Classical").displayName)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `curated genres match ShoutKit`() {
        assertEquals(18, Tag.CURATED.size)
        assertEquals("pop", Tag.CURATED.first().name)
        assertTrue(Tag.CURATED.any { it.name == "hip hop" })
        assertEquals(Tag.CURATED.size, Tag.CURATED.map { it.name }.toSet().size)
    }
}
