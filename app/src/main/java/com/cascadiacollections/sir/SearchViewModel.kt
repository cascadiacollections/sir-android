package com.cascadiacollections.sir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.StationSearchFilters
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.core.directory.search
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.RecentShelfStore
import com.cascadiacollections.sir.core.persistence.StationCollections
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the result area of the browse tab is showing. */
sealed interface SearchPhase {
    /** Nothing asked: genre chips and popular stations. */
    data object Idle : SearchPhase

    /** A request is in flight (or about to be, once the debounce settles). */
    data object Searching : SearchPhase

    data class Results(val stations: List<Station>) : SearchPhase

    /** The directory answered, with nothing. */
    data object Empty : SearchPhase

    /** The directory could not answer; [SearchViewModel.retry] repeats the same request. */
    data object Failed : SearchPhase
}

/**
 * Browse/search state, mirroring ShoutKit's search screen.
 *
 * [selectedGenre] non-null means genre mode: the field shows the genre's display name
 * and results come from a tag browse rather than a name search.
 */
data class SearchUiState(
    val query: String = "",
    val selectedGenre: Tag? = null,
    val genres: List<Tag> = Tag.CURATED,
    val filters: StationSearchFilters = StationSearchFilters.NONE,
    val phase: SearchPhase = SearchPhase.Idle,
    val popularStations: List<Station> = emptyList(),
    val isLoadingPopular: Boolean = false,
    /** The last attempt to load popular stations failed (whatever is shown is older). */
    val popularLoadFailed: Boolean = false,
    /** A pull-to-refresh is in flight. */
    val isRefreshing: Boolean = false,
    /** The Recently Played shelf: newest recents, minus the ones the user hid. */
    val recentShelf: List<Station> = emptyList()
) {
    /** Genre chips are offered when nothing is typed, and kept while browsing a genre. */
    val showsGenres: Boolean get() = query.isBlank() || selectedGenre != null

    /** ShoutKit titles the popular grid only when there is a shelf above it to separate it from. */
    val showsPopularHeader: Boolean get() = recentShelf.isNotEmpty()

    /** Nothing to show and the directory failed: "Directory unavailable" with a retry. */
    val showsDirectoryUnavailable: Boolean
        get() = popularLoadFailed && popularStations.isEmpty() && !isLoadingPopular && !isRefreshing

    /** A refresh failed over stations already on screen: keep them, say they are not live. */
    val showsSavedStationsNotice: Boolean get() = popularLoadFailed && popularStations.isNotEmpty()
}

/**
 * Drives the browse tab's search: search-as-you-type, genre browse and filters.
 *
 * Every request goes through one pipeline — debounced for typing, immediate for
 * everything else — collected with `collectLatest`, so a newer request always cancels
 * the one in flight. A generation counter additionally guards each write, because a
 * directory call can complete between the cancellation being requested and observed.
 *
 * Saving and playing stay on [RadioBrowserViewModel], which the library tab shares.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val directory: RadioDirectory,
    private val shelfStore: RecentShelfStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private sealed interface Request {
        val generation: Int

        data class Idle(override val generation: Int) : Request
        data class Name(
            val text: String,
            val filters: StationSearchFilters,
            val debounced: Boolean,
            override val generation: Int
        ) : Request
        data class Genre(
            val tag: Tag,
            val filters: StationSearchFilters,
            override val generation: Int
        ) : Request
    }

    private val requests = Channel<Request>(Channel.UNLIMITED)

    /** Bumped for every request; only the newest may write results. Main-thread only. */
    private var generation = 0

    /** The trimmed text of the last name request, so whitespace-only edits are ignored. */
    private var lastNameText: String? = null

    init {
        viewModelScope.launch {
            requests.receiveAsFlow()
                .debounce { if (it is Request.Name && it.debounced) DEBOUNCE_MILLIS else 0L }
                .collectLatest(::execute)
        }
        loadPopularStations()
        loadGenres()
        shelfStore?.let(::observeShelf)
    }

    private fun observeShelf(store: RecentShelfStore) {
        viewModelScope.launch {
            combine(store.recentStations, store.hiddenRecentStationIds, StationCollections::recentShelf)
                .collect { shelf -> _uiState.update { it.copy(recentShelf = shelf) } }
        }
    }

    /**
     * Removes [station] from the Recently Played shelf only — the Library's history keeps it.
     * The tile goes at once rather than when the store echoes the write back.
     */
    fun hideFromRecentlyPlayed(station: Station) {
        val store = shelfStore ?: return
        _uiState.update { state -> state.copy(recentShelf = state.recentShelf.filterNot { it.id == station.id }) }
        viewModelScope.launch { store.hideRecentStation(station.id) }
    }

    /** The snackbar's Undo for [hideFromRecentlyPlayed]. */
    fun undoHideFromRecentlyPlayed(station: Station) {
        val store = shelfStore ?: return
        viewModelScope.launch { store.unhideRecentStation(station.id) }
    }

    /**
     * Pull-to-refresh: reloads popular stations and genres past the directory's cache. A
     * failure keeps whatever is on screen and flags it ([SearchUiState.showsSavedStationsNotice]).
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        popularJob?.cancel()
        popularJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val tags = async { directory.topTags(RadioDirectory.DEFAULT_TAG_LIMIT, forceRefresh = true) }
            val stations = directory.topStations(POPULAR_LIMIT, forceRefresh = true)
            tags.await().onSuccess { genres ->
                if (genres.isNotEmpty()) _uiState.update { it.copy(genres = genres) }
            }
            ensureActive()
            // A superseded load's flags are cleared here, since its own final write never ran.
            _uiState.update { it.withPopularResult(stations).copy(isRefreshing = false, isLoadingPopular = false) }
        }
    }

    /** "Try again" on the directory-unavailable state. */
    fun retryPopular() {
        if (_uiState.value.isLoadingPopular || _uiState.value.isRefreshing) return
        loadPopularStations()
    }

    /** The field changed. Clearing it resets to idle at once; typing searches by name. */
    fun onQueryChange(text: String) {
        val state = _uiState.value
        val trimmed = text.trim()

        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(query = text, selectedGenre = null, phase = SearchPhase.Idle) }
            lastNameText = null
            submit(Request.Idle(nextGeneration()))
            return
        }

        val genre = state.selectedGenre
        if (genre != null && text == genre.displayName) {
            _uiState.update { it.copy(query = text) }
            return
        }

        if (genre == null && trimmed == lastNameText) {
            _uiState.update { it.copy(query = text) }
            return
        }

        _uiState.update {
            it.copy(
                query = text,
                selectedGenre = null,
                // Previous results stay up while the debounce settles; only an idle
                // screen switches straight to the spinner.
                phase = if (it.phase == SearchPhase.Idle) SearchPhase.Searching else it.phase
            )
        }
        requestName(trimmed, state.filters, debounced = true)
    }

    /** IME search action: run the current query now, skipping the debounce. */
    fun submit() {
        rerun()
    }

    /** Browses [tag]; tapping the selected genre again clears back to idle. */
    fun selectGenre(tag: Tag) {
        if (_uiState.value.selectedGenre?.name == tag.name) {
            onQueryChange("")
            return
        }
        lastNameText = null
        _uiState.update {
            it.copy(query = tag.displayName, selectedGenre = tag, phase = SearchPhase.Searching)
        }
        submit(Request.Genre(tag, _uiState.value.filters, nextGeneration()))
    }

    fun setFilters(filters: StationSearchFilters) {
        if (filters == _uiState.value.filters) return
        _uiState.update { it.copy(filters = filters) }
        rerun()
    }

    fun clearFilters() = setFilters(StationSearchFilters.NONE)

    /** Repeats the current request, in the same mode (name or genre). */
    fun retry() = rerun()

    private fun rerun() {
        val state = _uiState.value
        val genre = state.selectedGenre
        val trimmed = state.query.trim()
        when {
            genre != null -> {
                _uiState.update { it.copy(phase = SearchPhase.Searching) }
                submit(Request.Genre(genre, state.filters, nextGeneration()))
            }
            trimmed.isNotEmpty() -> {
                _uiState.update { it.copy(phase = SearchPhase.Searching) }
                requestName(trimmed, state.filters, debounced = false)
            }
        }
    }

    private fun requestName(text: String, filters: StationSearchFilters, debounced: Boolean) {
        lastNameText = text
        submit(Request.Name(text, filters, debounced, nextGeneration()))
    }

    private fun nextGeneration(): Int = ++generation

    private fun submit(request: Request) {
        requests.trySend(request)
    }

    private suspend fun execute(request: Request) {
        if (request.generation != generation) return
        val result = when (request) {
            is Request.Idle -> return
            is Request.Name -> {
                _uiState.update { it.copy(phase = SearchPhase.Searching) }
                directory.search(request.text, SEARCH_LIMIT, request.filters)
            }
            is Request.Genre -> {
                _uiState.update { it.copy(phase = SearchPhase.Searching) }
                directory.stationsByTag(request.tag.name, SEARCH_LIMIT, request.filters)
            }
        }
        // A directory call may swallow cancellation into its Result; never publish for a
        // request that has since been superseded.
        currentCoroutineContext().ensureActive()
        if (request.generation != generation) return
        val phase = result.fold(
            onSuccess = { if (it.isEmpty()) SearchPhase.Empty else SearchPhase.Results(it) },
            onFailure = { SearchPhase.Failed }
        )
        _uiState.update { it.copy(phase = phase) }
    }

    /** The in-flight popular-stations load or refresh; a newer one supersedes it. */
    private var popularJob: Job? = null

    /**
     * Seeds the idle screen with the directory's most-played stations. The curated fallback
     * answers offline, so a failure here means even that had nothing to give.
     */
    private fun loadPopularStations() {
        popularJob?.cancel()
        popularJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPopular = true) }
            val result = directory.topStations(POPULAR_LIMIT)
            ensureActive()
            _uiState.update { it.withPopularResult(result).copy(isLoadingPopular = false, isRefreshing = false) }
        }
    }

    private fun SearchUiState.withPopularResult(result: Result<List<Station>>): SearchUiState =
        result.fold(
            onSuccess = { copy(popularStations = it, popularLoadFailed = false) },
            onFailure = { copy(popularLoadFailed = true) }
        )

    /** Replaces the curated genres with the live list; a failure keeps the curated ones. */
    private fun loadGenres() {
        viewModelScope.launch {
            directory.topTags().onSuccess { tags ->
                if (tags.isNotEmpty()) _uiState.update { it.copy(genres = tags) }
            }
        }
    }

    class Factory(
        private val directory: RadioDirectory,
        private val shelfStore: RecentShelfStore? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(directory, shelfStore) as T
        }
    }

    companion object {
        const val DEBOUNCE_MILLIS: Long = 300L
        const val SEARCH_LIMIT: Int = 40

        /** ShoutKit's Listen Now grid size. */
        const val POPULAR_LIMIT: Int = 24
    }
}
