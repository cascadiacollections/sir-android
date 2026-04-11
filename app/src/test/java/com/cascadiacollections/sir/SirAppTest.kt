package com.cascadiacollections.sir

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [SirApp] Application class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SirApp::class)
class SirAppTest {

    @Test
    fun `application creates successfully`() {
        val app = RuntimeEnvironment.getApplication()
        assertNotNull(app)
    }

    @Test
    fun `application is instance of SirApp`() {
        val app = RuntimeEnvironment.getApplication()
        assertNotNull(app as? SirApp)
    }

    @Test
    fun `application context is available`() {
        val app = RuntimeEnvironment.getApplication()
        assertNotNull(app.applicationContext)
    }

    @Test
    fun `application package name is correct`() {
        val app = RuntimeEnvironment.getApplication()
        assertNotNull(app.packageName)
    }
}
