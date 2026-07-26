package com.cascadiacollections.sir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.search
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        viewModelScope.launch {
            settingsRepository.savedStations.collect { stations ->
                _uiState.value = _uiState.value.copy(savedStations = stations)
            }
        }
        viewModelScope.launch {
            settingsRepository.recentStations.collect { stations ->
                _uiState.value = _uiState.value.copy(recentStations = stations)
            }
        }
        viewModelScope.launch {
            settingsRepository.selectedStation.collect { station ->
                _uiState.value = _uiState.value.copy(selectedStationId = station?.id)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                error = "Enter a search query"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            directory.search(query)
                .onSuccess { stations ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = stations,
                        isLoading = false,
                        error = if (stations.isEmpty()) "No stations found" else null
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = emptyList(),
                        isLoading = false,
                        error = e.message ?: "Search failed"
                    )
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
}
