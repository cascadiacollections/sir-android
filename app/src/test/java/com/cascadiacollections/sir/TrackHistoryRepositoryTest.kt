package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.persistence.HeardTrack
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.core.persistence.TrackHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The persisted Recently Heard store, through a real DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackHistoryRepositoryTest {

    private val app get() = RuntimeEnvironment.getApplication()

    private fun repo() = TrackHistoryRepository(app)

    private fun heard(title: String, at: Long) =
        HeardTrack(title = title, artist = "Artist", stationId = "s1", stationName = "Station", timestampMillis = at)

    @Before
    fun reset() = runBlocking { repo().clear() }

    @Test
    fun `records newest first and merges a consecutive repeat`() = runBlocking {
        val repo = repo()
        repo.record(heard("A", 1))
        repo.record(heard("B", 2))
        repo.record(heard("B", 3))

        val tracks = repo.tracks.first()
        assertEquals(listOf("B", "A"), tracks.map { it.title })
        assertEquals(3L, tracks.first().timestampMillis)
    }

    @Test
    fun `history survives a new repository instance`() = runBlocking {
        repo().record(heard("A", 1))
        assertEquals(listOf("A"), repo().tracks.first().map { it.title })
    }

    @Test
    fun `clear empties the history`() = runBlocking {
        val repo = repo()
        repo.record(heard("A", 1))
        repo.clear()
        assertTrue(repo.tracks.first().isEmpty())
    }

    @Test
    fun `history is stored apart from settings`() = runBlocking {
        val settings = SettingsRepository(app)
        settings.clearRecentStations()
        repo().record(heard("A", 1))

        // Both stores are live on the same Application without contending for one file.
        assertTrue(settings.recentStations.first().isEmpty())
        assertTrue(app.filesDir.resolve("datastore/track_history.preferences_pb").exists())
    }
}
