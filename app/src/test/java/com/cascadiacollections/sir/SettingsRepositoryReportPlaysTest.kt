package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertTrue(repo.reportPlaysToDirectory.first())

        repo.setReportPlaysToDirectory(false)
        assertFalse(repo().reportPlaysToDirectory.first())

        repo.setReportPlaysToDirectory(true)
        assertTrue(repo.reportPlaysToDirectory.first())
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
        assertEquals(a, repo.selectedStation.first())
        repo.selectStation(b)
        repo.selectStation(a)

        assertEquals(listOf("a", "b", "a"), received.await().map { it.id })
    }
}
