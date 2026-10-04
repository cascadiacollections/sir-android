package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import org.junit.Test

class CastModuleStateTest {

    @Test
    fun `NotInstalled is a singleton`() {
        assertThat(CastModuleState.NotInstalled).isSameInstanceAs(CastModuleState.NotInstalled)
    }

    @Test
    fun `Installed is a singleton`() {
        assertThat(CastModuleState.Installed).isSameInstanceAs(CastModuleState.Installed)
    }

    @Test
    fun `Installing carries progress and supports data class equality`() {
        assertThat(CastModuleState.Installing(0.5f)).isEqualTo(CastModuleState.Installing(0.5f))
    }

    @Test
    fun `Installing with different progress values are not equal`() {
        assertThat(CastModuleState.Installing(0.7f)).isNotEqualTo(CastModuleState.Installing(0.3f))
    }

    @Test
    fun `Installing progress edge cases`() {
        val zero = CastModuleState.Installing(0f)
        val half = CastModuleState.Installing(0.5f)
        val full = CastModuleState.Installing(1f)

        assertThat(zero.progress).isCloseTo(0f, 0f)
        assertThat(half.progress).isCloseTo(0.5f, 0f)
        assertThat(full.progress).isCloseTo(1f, 0f)
    }

    @Test
    fun `Failed carries errorCode and supports data class equality`() {
        assertThat(CastModuleState.Failed(42)).isEqualTo(CastModuleState.Failed(42))
    }

    @Test
    fun `Failed with different error codes are not equal`() {
        assertThat(CastModuleState.Failed(2)).isNotEqualTo(CastModuleState.Failed(1))
    }
}
