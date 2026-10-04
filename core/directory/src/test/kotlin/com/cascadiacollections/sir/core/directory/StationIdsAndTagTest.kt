package com.cascadiacollections.sir.core.directory

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.util.Locale
import org.junit.Test

class StationIdsAndTagTest {

    @Test
    fun `radio-browser uuids are recognised`() {
        assertThat(StationIds.isRadioBrowserUuid("96062a7b-0601-11e8-ae97-52543be04c81")).isTrue()
        assertThat(StationIds.isRadioBrowserUuid("96062A7B-0601-11E8-AE97-52543BE04C81")).isTrue()
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
        ).forEach { assertThat(StationIds.isRadioBrowserUuid(it), name = it).isFalse() }
    }

    @Test
    fun `tag display names are capitalized per word without locale effects`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            assertThat(Tag("indie").displayName).isEqualTo("Indie")
            assertThat(Tag("hip hop").displayName).isEqualTo("Hip Hop")
            assertThat(Tag("80s").displayName).isEqualTo("80s")
            assertThat(Tag("Classical").displayName).isEqualTo("Classical")
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `curated genres match ShoutKit`() {
        assertThat(Tag.CURATED).hasSize(18)
        assertThat(Tag.CURATED.first().name).isEqualTo("pop")
        assertThat(Tag.CURATED.map { it.name }).contains("hip hop")
        assertThat(Tag.CURATED.map { it.name }.toSet()).hasSize(Tag.CURATED.size)
    }
}
