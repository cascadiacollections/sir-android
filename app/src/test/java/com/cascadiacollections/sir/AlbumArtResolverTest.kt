package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.artwork.AlbumArt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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

        assertThat(resolver.current).isEqualTo(ART)
        assertThat(changes).isEqualTo(1)
    }

    @Test
    fun `a new track clears the previous art and reports it`() {
        resolver.onTrackChanged("Artist", "Title")
        answer("Artist", "Title", ART)

        assertThat(resolver.onTrackChanged("Other", "Song")).isTrue()
        assertThat(resolver.current).isNull()
    }

    @Test
    fun `a late answer for a track that has moved on is dropped`() {
        resolver.onTrackChanged("Artist", "Title")
        resolver.onTrackChanged("Other", "Song")
        answer("Artist", "Title", ART)

        assertThat(resolver.current).isNull()
        assertThat(changes).isEqualTo(0)
    }

    @Test
    fun `the same track again does not look up twice`() {
        resolver.onTrackChanged("Artist", "Title")
        assertThat(resolver.onTrackChanged("Artist", "Title")).isFalse()
        assertThat(requests).hasSize(1)
    }

    @Test
    fun `no lookup at all while disabled, and art is dropped when it is switched off`() {
        resolver.onTrackChanged("Artist", "Title")
        answer("Artist", "Title", ART)

        enabled.value = false
        scope.runCurrent()
        assertThat(resolver.current).isNull()
        assertThat(changes).isEqualTo(2)

        resolver.onTrackChanged("Other", "Song")
        assertThat(requests).containsExactly("Artist" to "Title")
    }

    @Test
    fun `switching it back on looks up the current track`() {
        enabled.value = false
        scope.runCurrent()
        resolver.onTrackChanged("Artist", "Title")
        assertThat(requests).isEmpty()

        enabled.value = true
        scope.runCurrent()
        answer("Artist", "Title", ART)
        assertThat(resolver.current).isEqualTo(ART)
    }

    @Test
    fun `a track without an artist is not looked up`() {
        resolver.onTrackChanged(null, "Station jingle")
        assertThat(requests).isEmpty()
    }

    private companion object {
        val ART = AlbumArt("https://a/600x600bb.jpg", "https://music.apple.com/t")
    }
}
