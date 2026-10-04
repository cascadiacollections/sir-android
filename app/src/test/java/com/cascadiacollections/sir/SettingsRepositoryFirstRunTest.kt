package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Round-trips for the first-run, connection-prewarming and loop-broadcasts flags (runBlocking: real DataStore IO). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryFirstRunTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun `first run completion persists`() = runBlocking {
        val repo = repo()
        repo.setHasCompletedFirstRun(false)
        assertFalse(repo.hasCompletedFirstRun.first())

        repo.setHasCompletedFirstRun(true)

        assertTrue(repo().hasCompletedFirstRun.first())
    }

    @Test
    fun `connection prewarming toggle persists`() = runBlocking {
        val repo = repo()
        repo.setConnectionPrewarmingEnabled(true)
        assertTrue(repo.connectionPrewarmingEnabled.first())

        repo.setConnectionPrewarmingEnabled(false)

        assertFalse(repo().connectionPrewarmingEnabled.first())
    }

    @Test
    fun `loop finished broadcasts is off by default and persists`() = runBlocking {
        val repo = repo()
        repo.setLoopFinishedBroadcasts(false)
        assertFalse(repo.loopFinishedBroadcasts.first())

        repo.setLoopFinishedBroadcasts(true)

        assertTrue(repo().loopFinishedBroadcasts.first())
        repo.setLoopFinishedBroadcasts(false)
    }
}
