package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The "Report plays to Radio Browser" setting and the selection events that drive it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryReportPlaysTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun `report plays setting persists changes`() = runBlocking {
        val repo = repo()
        repo.setReportPlaysToDirectory(true)
        assertThat(repo.reportPlaysToDirectory.first()).isTrue()

        repo.setReportPlaysToDirectory(false)
        assertThat(repo().reportPlaysToDirectory.first()).isFalse()

        repo.setReportPlaysToDirectory(true)
        assertThat(repo.reportPlaysToDirectory.first()).isTrue()
    }

    @Test
    fun `every selection is announced once it is persisted`() = runBlocking {
        val repo = repo()
        val a = Station(id = "a", name = "A", url = "https://example.com/a")
        val b = Station(id = "b", name = "B", url = "https://example.com/b")

        val received = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { SettingsRepository.stationSelections.take(3).toList() }
        }
        repo.selectStation(a)
        assertThat(repo.selectedStation.first()).isEqualTo(a)
        repo.selectStation(b)
        repo.selectStation(a)

        assertThat(received.await().map { it.id }).containsExactly("a", "b", "a")
    }
}
