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

/** The "Fetch album artwork" privacy setting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryFetchAlbumArtworkTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun `fetch album artwork persists changes and defaults on`() = runBlocking {
        val repo = repo()
        repo.setFetchAlbumArtwork(false)
        assertFalse(repo().fetchAlbumArtwork.first())

        repo.setFetchAlbumArtwork(true)
        assertTrue(repo.fetchAlbumArtwork.first())
    }
}
