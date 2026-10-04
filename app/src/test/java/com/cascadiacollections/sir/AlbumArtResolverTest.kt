package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.artwork.AlbumArt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumArtResolverTest {

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val enabled = MutableStateFlow(true)
    private val requests = mutableListOf<Pair<String, String>>()
    private val answers = mutableMapOf<Pair<String, String>, CompletableDeferred<AlbumArt?>>()
    private var changes = 0

    private val resolver = AlbumArtResolver(
        scope = scope,
        enabled = enabled,
        lookup = { artist, title ->
            requests += artist to title
            answers.getOrPut(artist to title) { CompletableDeferred() }.await()
        },
        onChanged = { changes++ }
    ).also { it.start() }

    private fun answer(artist: String, title: String, art: AlbumArt?) {
        answers.getOrPut(artist to title) { CompletableDeferred() }.complete(art)
        scope.runCurrent()
    }

    @Test
    fun `a resolved track's art becomes current`() {
        resolver.onTrackChanged("Artist", "Title")
        answer("Artist", "Title", ART)

        assertEquals(ART, resolver.current)
        assertEquals(1, changes)
    }

    @Test
    fun `a new track clears the previous art and reports it`() {
        resolver.onTrackChanged("Artist", "Title")
        answer("Artist", "Title", ART)

        assertTrue(resolver.onTrackChanged("Other", "Song"))
        assertNull(resolver.current)
    }

    @Test
    fun `a late answer for a track that has moved on is dropped`() {
        resolver.onTrackChanged("Artist", "Title")
        resolver.onTrackChanged("Other", "Song")
        answer("Artist", "Title", ART)

        assertNull(resolver.current)
        assertEquals(0, changes)
    }

    @Test
    fun `the same track again does not look up twice`() {
        resolver.onTrackChanged("Artist", "Title")
        assertFalse(resolver.onTrackChanged("Artist", "Title"))
        assertEquals(1, requests.size)
    }

    @Test
    fun `no lookup at all while disabled, and art is dropped when it is switched off`() {
        resolver.onTrackChanged("Artist", "Title")
        answer("Artist", "Title", ART)

        enabled.value = false
        scope.runCurrent()
        assertNull(resolver.current)
        assertEquals(2, changes)

        resolver.onTrackChanged("Other", "Song")
        assertEquals(listOf("Artist" to "Title"), requests)
    }

    @Test
    fun `switching it back on looks up the current track`() {
        enabled.value = false
        scope.runCurrent()
        resolver.onTrackChanged("Artist", "Title")
        assertTrue(requests.isEmpty())

        enabled.value = true
        scope.runCurrent()
        answer("Artist", "Title", ART)
        assertEquals(ART, resolver.current)
    }

    @Test
    fun `a track without an artist is not looked up`() {
        resolver.onTrackChanged(null, "Station jingle")
        assertTrue(requests.isEmpty())
    }

    private companion object {
        val ART = AlbumArt("https://a/600x600bb.jpg", "https://music.apple.com/t")
    }
}
