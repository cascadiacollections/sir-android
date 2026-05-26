package com.cascadiacollections.sir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RadioBrowserUiState(
    val searchQuery: String = "",
    val searchResults: List<RadioBrowserStation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedStations: List<RadioBrowserStation> = emptyList()
)

class RadioBrowserViewModel(
    private val radioBrowserService: RadioBrowserService,
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

            val result = radioBrowserService.searchStations(query)
            result.onSuccess { stations ->
                _uiState.value = _uiState.value.copy(
                    searchResults = stations,
                    isLoading = false,
                    error = if (stations.isEmpty()) "No stations found" else null
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isLoading = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }

    fun saveStation(station: RadioBrowserStation) {
        viewModelScope.launch {
            settingsRepository.saveStation(station)
        }
    }

    fun removeStation(stationId: String) {
        viewModelScope.launch {
            settingsRepository.removeStation(stationId)
        }
    }

    fun isStationSaved(station: RadioBrowserStation): Boolean {
        return _uiState.value.savedStations.any { it.id == station.id }
    }
}
