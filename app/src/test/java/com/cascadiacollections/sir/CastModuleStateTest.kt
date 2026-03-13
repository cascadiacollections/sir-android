package com.cascadiacollections.sir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CastModuleStateTest {

    @Test
    fun `NotInstalled is a singleton`() {
        assertSame(CastModuleState.NotInstalled, CastModuleState.NotInstalled)
    }

    @Test
    fun `Installed is a singleton`() {
        assertSame(CastModuleState.Installed, CastModuleState.Installed)
    }

    @Test
    fun `Installing carries progress and supports data class equality`() {
        assertEquals(CastModuleState.Installing(0.5f), CastModuleState.Installing(0.5f))
    }

    @Test
    fun `Installing with different progress values are not equal`() {
        assertNotEquals(CastModuleState.Installing(0.3f), CastModuleState.Installing(0.7f))
    }

    @Test
    fun `Installing progress edge cases`() {
        val zero = CastModuleState.Installing(0f)
        val half = CastModuleState.Installing(0.5f)
        val full = CastModuleState.Installing(1f)

        assertEquals(0f, zero.progress, 0f)
        assertEquals(0.5f, half.progress, 0f)
        assertEquals(1f, full.progress, 0f)
    }

    @Test
    fun `Failed carries errorCode and supports data class equality`() {
        assertEquals(CastModuleState.Failed(42), CastModuleState.Failed(42))
    }

    @Test
    fun `Failed with different error codes are not equal`() {
        assertNotEquals(CastModuleState.Failed(1), CastModuleState.Failed(2))
    }
}
