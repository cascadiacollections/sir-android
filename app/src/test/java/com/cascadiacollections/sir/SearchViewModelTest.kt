package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.StationSearchFilters
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val directory = FakeDirectory()

    private fun viewModel() = SearchViewModel(directory).also(coroutineRule::registerViewModel)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(coroutineRule.testDispatcher) {
        block()
    }

    private fun TestScope.settle() {
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()
    }

    @Test
    fun `opens idle with popular stations and the live genre list`() = test {
        val vm = viewModel()
        runCurrent()

        val state = vm.uiState.value
        assertThat(state.phase).isEqualTo(SearchPhase.Idle)
        assertThat(state.popularStations).containsExactly(POPULAR)
        assertThat(state.genres).containsExactly(Tag("synthwave", 12))
        assertThat(state.showsGenres).isTrue()
    }

    @Test
    fun `a genre list failure keeps the curated genres`() = test {
        directory.topTagsResult = Result.failure(RuntimeException("offline"))
        val vm = viewModel()
        runCurrent()

        assertThat(vm.uiState.value.genres).isEqualTo(Tag.CURATED)
    }

    @Test
    fun `typing searches only once the debounce has elapsed`() = test {
        val vm = viewModel()
        vm.onQueryChange("rock")

        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertThat(directory.calls).isEmpty()
        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Searching)

        advanceTimeBy(2)
        runCurrent()
        assertThat(directory.calls).containsExactly("name:rock")
        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Results(listOf(station("rock"))))
    }

    @Test
    fun `rapid typing searches only the final text, with the search limit`() = test {
        val vm = viewModel()
        vm.onQueryChange("r")
        advanceTimeBy(100)
        vm.onQueryChange("ro")
        advanceTimeBy(100)
        vm.onQueryChange("roc")
        settle()

        assertThat(directory.calls).containsExactly("name:roc")
        assertThat(directory.lastLimit).isEqualTo(SearchViewModel.SEARCH_LIMIT)
    }

    @Test
    fun `whitespace-only edits do not search again`() = test {
        val vm = viewModel()
        vm.onQueryChange("jazz")
        settle()
        vm.onQueryChange("jazz ")
        settle()
        vm.onQueryChange(" jazz ")
        settle()

        assertThat(directory.calls).containsExactly("name:jazz")
        assertThat(vm.uiState.value.query).isEqualTo(" jazz ")
    }

    @Test
    fun `clearing the field resets to idle immediately and drops a pending search`() = test {
        val vm = viewModel()
        vm.onQueryChange("jazz")
        settle()
        vm.onQueryChange("jazzy")
        vm.onQueryChange("")

        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Idle)
        settle()
        assertThat(directory.calls).containsExactly("name:jazz")
        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Idle)
    }

    @Test
    fun `submitting skips the debounce`() = test {
        val vm = viewModel()
        vm.onQueryChange("news")
        vm.submit()
        runCurrent()
        assertThat(directory.calls).containsExactly("name:news")

        settle()
        assertThat(directory.calls).containsExactly("name:news")
    }

    @Test
    fun `tapping a genre runs a tag search and shows the genre in the field`() = test {
        val vm = viewModel()
        vm.selectGenre(Tag("hip hop"))
        runCurrent()

        val state = vm.uiState.value
        assertThat(directory.calls).containsExactly("tag:hip hop")
        assertThat(state.query).isEqualTo("Hip Hop")
        assertThat(state.selectedGenre).isEqualTo(Tag("hip hop"))
        assertThat(state.showsGenres).isTrue()
        assertThat(state.phase).isEqualTo(SearchPhase.Results(listOf(station("tag hip hop"))))
    }

    @Test
    fun `typing over a genre switches back to name search`() = test {
        val vm = viewModel()
        vm.selectGenre(Tag("jazz"))
        runCurrent()
        vm.onQueryChange("Jazz FM")
        settle()

        assertThat(vm.uiState.value.selectedGenre).isNull()
        assertThat(directory.calls).containsExactly("tag:jazz", "name:Jazz FM")
    }

    @Test
    fun `tapping the selected genre again clears to idle`() = test {
        val vm = viewModel()
        vm.selectGenre(Tag("jazz"))
        runCurrent()
        vm.selectGenre(Tag("jazz", stationCount = 99))

        val state = vm.uiState.value
        assertThat(state.selectedGenre).isNull()
        assertThat(state.query).isEmpty()
        assertThat(state.phase).isEqualTo(SearchPhase.Idle)
    }

    @Test
    fun `changing filters reruns the current genre with them`() = test {
        val vm = viewModel()
        vm.selectGenre(Tag("rock"))
        runCurrent()
        val filters = StationSearchFilters(bitrateMinKbps = 128, countryCode = "us")
        vm.setFilters(filters)
        runCurrent()

        assertThat(directory.calls).containsExactly("tag:rock", "tag:rock")
        assertThat(directory.lastFilters).isEqualTo(filters)
        assertThat(vm.uiState.value.filters).isEqualTo(filters)
    }

    @Test
    fun `changing filters reruns a name search, and clearing them reruns again`() = test {
        val vm = viewModel()
        vm.onQueryChange("talk")
        settle()
        vm.setFilters(StationSearchFilters(tag = "news"))
        runCurrent()
        vm.clearFilters()
        runCurrent()

        assertThat(directory.calls).containsExactly("name:talk", "name:talk", "name:talk")
        assertThat(directory.lastFilters).isEqualTo(StationSearchFilters.NONE)
    }

    @Test
    fun `filters set while idle are kept for the next search without searching`() = test {
        val vm = viewModel()
        val filters = StationSearchFilters(bitrateMaxKbps = 96)
        vm.setFilters(filters)
        runCurrent()
        assertThat(directory.calls).isEmpty()

        vm.onQueryChange("lofi")
        settle()
        assertThat(directory.lastFilters).isEqualTo(filters)
    }

    @Test
    fun `an empty answer is reported as empty`() = test {
        directory.searchResult = { Result.success(emptyList()) }
        val vm = viewModel()
        vm.onQueryChange("zzzz")
        settle()

        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Empty)
    }

    @Test
    fun `retry after a failed genre browse repeats the genre browse`() = test {
        directory.tagResult = { Result.failure(RuntimeException("down")) }
        val vm = viewModel()
        vm.selectGenre(Tag("jazz"))
        runCurrent()
        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Failed)

        directory.tagResult = { Result.success(listOf(station("tag $it"))) }
        vm.retry()
        runCurrent()

        assertThat(directory.calls).containsExactly("tag:jazz", "tag:jazz")
        assertThat(vm.uiState.value.selectedGenre).isEqualTo(Tag("jazz"))
        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Results(listOf(station("tag jazz"))))
    }

    @Test
    fun `retry after a failed name search repeats the name search`() = test {
        directory.searchResult = { Result.failure(RuntimeException("down")) }
        val vm = viewModel()
        vm.onQueryChange("bbc")
        settle()
        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Failed)

        directory.searchResult = { Result.success(listOf(station(it))) }
        vm.retry()
        runCurrent()

        assertThat(directory.calls).containsExactly("name:bbc", "name:bbc")
        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Results(listOf(station("bbc"))))
    }

    @Test
    fun `a superseded search never publishes its results`() = test {
        val gate = CompletableDeferred<Unit>()
        directory.gates["slow"] = gate
        val vm = viewModel()
        vm.onQueryChange("slow")
        settle()
        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Searching)

        vm.onQueryChange("fast")
        settle()
        gate.complete(Unit)
        runCurrent()

        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Results(listOf(station("fast"))))
    }

    @Test
    fun `a superseded search that swallows cancellation is still ignored`() = test {
        // A directory that converts cancellation into a failed Result, as a careless
        // runCatching would: the result must still not reach the screen.
        val gate = CompletableDeferred<Unit>()
        directory.gates["slow"] = gate
        directory.swallowCancellation = true
        val vm = viewModel()
        vm.onQueryChange("slow")
        settle()

        vm.onQueryChange("")
        runCurrent()

        assertThat(vm.uiState.value.phase).isEqualTo(SearchPhase.Idle)
    }

    private class FakeDirectory : RadioDirectory {
        val calls = mutableListOf<String>()
        var lastFilters: StationSearchFilters? = null
        var lastLimit: Int? = null
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var swallowCancellation = false
        var searchResult: (String) -> Result<List<Station>> = {
            Result.success(listOf(station(it)))
        }
        var tagResult: (String) -> Result<List<Station>> = {
            Result.success(listOf(station("tag $it")))
        }
        var topTagsResult: Result<List<Tag>> = Result.success(listOf(Tag("synthwave", 12)))

        override suspend fun search(query: StationQuery): Result<List<Station>> =
            search(query, StationSearchFilters.NONE)

        override suspend fun search(query: StationQuery, filters: StationSearchFilters): Result<List<Station>> {
            calls += "name:${query.normalizedText}"
            lastFilters = filters
            lastLimit = query.limit
            gates[query.normalizedText]?.let { gate ->
                if (swallowCancellation) {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        gate.await()
                    } catch (e: Exception) {
                        return Result.failure(e)
                    }
                } else {
                    gate.await()
                }
            }
            return searchResult(query.normalizedText)
        }

        override suspend fun topStations(limit: Int): Result<List<Station>> = Result.success(listOf(POPULAR))

        override suspend fun stationsByTag(tag: String, limit: Int): Result<List<Station>> =
            stationsByTag(tag, limit, StationSearchFilters.NONE)

        override suspend fun stationsByTag(
            tag: String,
            limit: Int,
            filters: StationSearchFilters
        ): Result<List<Station>> {
            calls += "tag:$tag"
            lastFilters = filters
            lastLimit = limit
            return tagResult(tag)
        }

        override suspend fun getStation(id: String): Result<Station?> = Result.success(null)

        override suspend fun topTags(limit: Int): Result<List<Tag>> = topTagsResult
    }

    private companion object {
        val POPULAR = station("popular")

        fun station(name: String) = Station(id = name, name = name, url = "https://example.com/$name")
    }
}
