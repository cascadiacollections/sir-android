package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        assertThat(repo.hasCompletedFirstRun.first()).isFalse()

        repo.setHasCompletedFirstRun(true)

        assertThat(repo().hasCompletedFirstRun.first()).isTrue()
    }

    @Test
    fun `connection prewarming toggle persists`() = runBlocking {
        val repo = repo()
        repo.setConnectionPrewarmingEnabled(true)
        assertThat(repo.connectionPrewarmingEnabled.first()).isTrue()

        repo.setConnectionPrewarmingEnabled(false)

        assertThat(repo().connectionPrewarmingEnabled.first()).isFalse()
    }

    @Test
    fun `loop finished broadcasts is off by default and persists`() = runBlocking {
        val repo = repo()
        repo.setLoopFinishedBroadcasts(false)
        assertThat(repo.loopFinishedBroadcasts.first()).isFalse()

        repo.setLoopFinishedBroadcasts(true)

        assertThat(repo().loopFinishedBroadcasts.first()).isTrue()
        repo.setLoopFinishedBroadcasts(false)
    }
}
