package com.cascadiacollections.sir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.search
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.PlaylistCodec
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RadioBrowserUiState(
    val searchQuery: String = "",
    val searchResults: List<Station> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedStations: List<Station> = emptyList(),
    val recentStations: List<Station> = emptyList(),
    val selectedStationId: String? = null
)

/** Outcome of [RadioBrowserViewModel.importPlaylist], reported back to the UI for a toast. */
sealed interface PlaylistImportResult {
    data class Imported(val added: Int, val skipped: Int) : PlaylistImportResult
    data object Empty : PlaylistImportResult
}

/**
 * Drives the station discovery surface.
 *
 * Depends only on the [RadioDirectory] boundary, so caching, mirror rotation and
 * curated fallback are swappable without touching the UI layer.
 */
class RadioBrowserViewModel(
    private val directory: RadioDirectory,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadioBrowserUiState())
    val uiState: StateFlow<RadioBrowserUiState> = _uiState.asStateFlow()

    /** The in-flight browse seed, cancelled as soon as the user runs a real search. */
    private var seedJob: Job? = null

    init {
        // These three collectors, the browse seed and search all write _uiState
        // concurrently. `update` applies each change to the value current at the moment
        // it commits; `value = value.copy(...)` read, copied and wrote as three separate
        // steps, so two collectors emitting together could each build on the same
        // snapshot and the second would silently drop the first's field.
        viewModelScope.launch {
            settingsRepository.savedStations.collect { stations ->
                _uiState.update { it.copy(savedStations = stations) }
            }
        }
        viewModelScope.launch {
            settingsRepository.recentStations.collect { stations ->
                _uiState.update { it.copy(recentStations = stations) }
            }
        }
        viewModelScope.launch {
            settingsRepository.selectedStation.collect { station ->
                _uiState.update { it.copy(selectedStationId = station?.id) }
            }
        }
        loadTopStations()
    }

    /**
     * Seeds browse with the directory's most-played stations so the tab opens with
     * something to listen to rather than an empty prompt.
     *
     * A failure here is silent: the curated fallback already answers with bundled
     * stations when the network is down, and an error banner on a screen the user has
     * not asked anything of yet is noise. Searching surfaces errors normally.
     *
     * The job is retained so [search] can cancel it. Both this and a search write
     * `isLoading`, so a seed that outlived the user's first search would otherwise clear
     * the flag underneath it and drop the spinner mid-search.
     */
    private fun loadTopStations() {
        seedJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = directory.topStations()
            // Cancellation can land between the request returning and this write; without
            // the check a superseded seed would still publish its results and clear the
            // loading state that now belongs to the search.
            ensureActive()
            _uiState.update { current ->
                current.copy(
                    searchResults = result.getOrDefault(current.searchResults),
                    isLoading = false
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            _uiState.update {
                it.copy(searchResults = emptyList(), error = "Enter a search query")
            }
            return
        }

        // The user has asked for something specific, so the seed is now irrelevant — and
        // must not report completion over this search's loading state.
        seedJob?.cancel()
        seedJob = null

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            directory.search(query)
                .onSuccess { stations ->
                    _uiState.update {
                        it.copy(
                            searchResults = stations,
                            isLoading = false,
                            error = if (stations.isEmpty()) "No stations found" else null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            searchResults = emptyList(),
                            isLoading = false,
                            error = e.message ?: "Search failed"
                        )
                    }
                }
        }
    }

    /**
     * Makes [station] the stream being played. The playback service observes the
     * persisted selection, so no direct hand-off to the service is needed.
     */
    fun playStation(station: Station) {
        if (!station.isPlayable) return
        viewModelScope.launch {
            settingsRepository.selectStation(station)
        }
    }

    /**
     * Reverts to the app's own stream. Without this, selecting a directory station
     * would be a one-way door.
     */
    fun playDefaultStream() {
        viewModelScope.launch {
            settingsRepository.clearSelectedStation()
        }
    }

    fun saveStation(station: Station) {
        viewModelScope.launch {
            settingsRepository.saveStation(station)
        }
    }

    fun removeStation(stationId: String) {
        viewModelScope.launch {
            settingsRepository.removeStation(stationId)
        }
    }

    fun isStationSaved(station: Station): Boolean {
        return _uiState.value.savedStations.any { it.id == station.id }
    }

    fun clearRecentStations() {
        viewModelScope.launch {
            settingsRepository.clearRecentStations()
        }
    }

    /**
     * Parses [text] as an M3U ([isPls] false) or PLS ([isPls] true) playlist and saves
     * any station whose URL isn't already saved. Dedupes by URL rather than id, since
     * imported entries carry no radio-browser id to match against.
     */
    fun importPlaylist(text: String, isPls: Boolean, onResult: (PlaylistImportResult) -> Unit) {
        viewModelScope.launch {
            val parsed = if (isPls) PlaylistCodec.parsePls(text) else PlaylistCodec.parseM3u(text)
            if (parsed.isEmpty()) {
                onResult(PlaylistImportResult.Empty)
                return@launch
            }

            val seenUrls = _uiState.value.savedStations.mapTo(mutableSetOf()) { it.url }
            var added = 0
            parsed.forEach { station ->
                if (seenUrls.add(station.url)) {
                    settingsRepository.saveStation(station)
                    added++
                }
            }
            onResult(PlaylistImportResult.Imported(added = added, skipped = parsed.size - added))
        }
    }

    /** Renders the current saved stations as M3U text for export/backup. */
    fun exportPlaylist(): String = PlaylistCodec.toM3u(_uiState.value.savedStations)

    class Factory(
        private val directory: RadioDirectory,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return RadioBrowserViewModel(directory, settingsRepository) as T
        }
    }
}
