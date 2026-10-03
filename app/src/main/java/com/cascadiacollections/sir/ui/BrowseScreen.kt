@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cascadiacollections.sir.ui

import android.content.res.Resources
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cascadiacollections.sir.AppDirectory
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioBrowserViewModel
import com.cascadiacollections.sir.SearchPhase
import com.cascadiacollections.sir.SearchUiState
import com.cascadiacollections.sir.SearchViewModel
import com.cascadiacollections.sir.core.directory.StationSearchFilters
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.core.model.Station

object BrowseScreenTestTags {
    const val SEARCH_FIELD = "browse_search_field"
    const val FILTER_BUTTON = "browse_filter_button"
    const val FILTER_TAG_FIELD = "browse_filter_tag"
    const val FILTER_COUNTRY_FIELD = "browse_filter_country"
    const val FILTER_MIN_BITRATE = "browse_filter_min_bitrate"
    const val FILTER_MAX_BITRATE = "browse_filter_max_bitrate"
    const val FILTER_DONE = "browse_filter_done"
    const val FILTER_CLEAR = "browse_filter_clear"
}

/** Bitrate choices offered by the filter sheet, matching ShoutKit; null is "Any". */
internal val BITRATE_OPTIONS_KBPS: List<Int?> = listOf(null, 64, 96, 128, 160, 192, 256, 320)

/**
 * Station discovery: search-as-you-type, genre browse and filters, as on ShoutKit.
 *
 * Search state lives in [SearchViewModel]; saving and playing go through the
 * [RadioBrowserViewModel] shared with the library tab. Saved and recently played
 * stations live on the library tab — this screen is only about finding something new.
 */
@Composable
fun BrowseScreen(
    viewModel: RadioBrowserViewModel,
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(AppDirectory.instance))
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val browserState by viewModel.uiState.collectAsState()
    val searchState by searchViewModel.uiState.collectAsState()
    val savedIds = remember(browserState.savedStations) {
        browserState.savedStations.mapTo(HashSet()) { it.id }
    }

    BrowseContent(
        state = searchState,
        savedStationIds = savedIds,
        selectedStationId = browserState.selectedStationId,
        onQueryChange = searchViewModel::onQueryChange,
        onSubmit = searchViewModel::submit,
        onSelectGenre = searchViewModel::selectGenre,
        onFiltersChange = searchViewModel::setFilters,
        onRetry = searchViewModel::retry,
        onPlay = viewModel::playStation,
        onToggleSaved = { station, isSaved ->
            if (isSaved) {
                viewModel.removeStation(station.id)
                Toast.makeText(context, resources.getString(R.string.station_removed), Toast.LENGTH_SHORT).show()
            } else {
                viewModel.saveStation(station)
                Toast.makeText(context, resources.getString(R.string.station_saved), Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier
    )
}

/** Stateless body of [BrowseScreen], split out so it can be tested without a directory. */
@Composable
fun BrowseContent(
    state: SearchUiState,
    savedStationIds: Set<String>,
    selectedStationId: String?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSelectGenre: (Tag) -> Unit,
    onFiltersChange: (StationSearchFilters) -> Unit,
    onRetry: () -> Unit,
    onPlay: (Station) -> Unit,
    onToggleSaved: (Station, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val resources = LocalResources.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_stations_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit()
                    keyboard?.hide()
                }),
                modifier = Modifier
                    .weight(1f)
                    .testTag(BrowseScreenTestTags.SEARCH_FIELD),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                }
            )
            FilterButton(
                active = state.filters.isActive,
                onClick = { showFilters = true }
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.showsGenres) {
                item(key = "genres_header") { SectionHeader(stringResource(R.string.browse_genres_header)) }
                item(key = "genres") {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.genres.forEach { tag ->
                            val selected = state.selectedGenre?.name == tag.name
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectGenre(tag) },
                                label = { Text(tag.displayName) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

            val stationRow: LazyListScope.(List<Station>) -> Unit = { stations ->
                // Unkeyed: directory results are not guaranteed unique, and a duplicate key crashes.
                items(stations) { station ->
                    val isSaved = station.id in savedStationIds
                    StationRow(
                        station = station,
                        isPlaying = station.id == selectedStationId,
                        onPlay = { onPlay(station) },
                        subtitle = station.browseSubtitle { resources.getString(R.string.bitrate_kbps, it) },
                        trailing = {
                            IconButton(onClick = { onToggleSaved(station, isSaved) }) {
                                if (isSaved) {
                                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.station_saved))
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.save_station))
                                }
                            }
                        }
                    )
                }
            }

            when (val phase = state.phase) {
                SearchPhase.Idle -> {
                    item(key = "popular_header") { SectionHeader(stringResource(R.string.popular_stations)) }
                    if (state.popularStations.isEmpty() && state.isLoadingPopular) {
                        item(key = "popular_loading") { LoadingRow() }
                    }
                    stationRow(state.popularStations)
                }

                SearchPhase.Searching -> item(key = "searching") { LoadingRow() }

                is SearchPhase.Results -> stationRow(phase.stations)

                SearchPhase.Empty -> item(key = "empty") {
                    MessageBlock(stringResource(R.string.search_no_results)) {
                        if (state.filters.isActive) {
                            Text(
                                text = filterSummary(resources, state.filters),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { onFiltersChange(StationSearchFilters.NONE) }) {
                                Text(stringResource(R.string.clear_filters))
                            }
                        }
                    }
                }

                SearchPhase.Failed -> item(key = "failed") {
                    MessageBlock(stringResource(R.string.search_unavailable)) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            filters = state.filters,
            onApply = onFiltersChange,
            onDismiss = { showFilters = false }
        )
    }
}

@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (active) R.string.search_filters_active else R.string.search_filters)
    val modifier = Modifier.testTag(BrowseScreenTestTags.FILTER_BUTTON)
    if (active) {
        FilledTonalIconButton(onClick = onClick, modifier = modifier) {
            BadgedBox(badge = { Badge() }) {
                Icon(Icons.Default.FilterList, contentDescription = description)
            }
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Default.FilterList, contentDescription = description)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageBlock(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

/**
 * Edits [filters] as a draft and applies it when the sheet closes ("Done" or a swipe),
 * so typing a tag or country does not rerun the search on every keystroke.
 */
@Composable
private fun FilterSheet(
    filters: StationSearchFilters,
    onApply: (StationSearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var minKbps by remember { mutableStateOf(filters.bitrateMinKbps) }
    var maxKbps by remember { mutableStateOf(filters.bitrateMaxKbps) }
    var tag by remember { mutableStateOf(filters.tag.orEmpty()) }
    var country by remember { mutableStateOf(filters.countryCode.orEmpty()) }
    val draft = StationSearchFilters(minKbps, maxKbps, tag, country)
    val close = {
        onApply(draft)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.search_filters), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BitratePicker(
                    label = stringResource(R.string.filter_min_bitrate),
                    value = minKbps,
                    onValueChange = { minKbps = it },
                    testTag = BrowseScreenTestTags.FILTER_MIN_BITRATE,
                    modifier = Modifier.weight(1f)
                )
                BitratePicker(
                    label = stringResource(R.string.filter_max_bitrate),
                    value = maxKbps,
                    onValueChange = { maxKbps = it },
                    testTag = BrowseScreenTestTags.FILTER_MAX_BITRATE,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                label = { Text(stringResource(R.string.filter_tag)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BrowseScreenTestTags.FILTER_TAG_FIELD)
            )
            OutlinedTextField(
                value = country,
                onValueChange = { input -> country = sanitizeCountryCode(input) },
                label = { Text(stringResource(R.string.filter_country_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BrowseScreenTestTags.FILTER_COUNTRY_FIELD)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(
                    onClick = {
                        minKbps = null
                        maxKbps = null
                        tag = ""
                        country = ""
                        onApply(StationSearchFilters.NONE)
                    },
                    enabled = draft.isActive || filters.isActive,
                    modifier = Modifier.testTag(BrowseScreenTestTags.FILTER_CLEAR)
                ) {
                    Text(stringResource(R.string.clear_filters))
                }
                Button(onClick = close, modifier = Modifier.testTag(BrowseScreenTestTags.FILTER_DONE)) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

@Composable
private fun BitratePicker(
    label: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val any = stringResource(R.string.filter_any)
    val resources = LocalResources.current
    fun labelFor(kbps: Int?) = kbps?.let { resources.getString(R.string.bitrate_kbps, it) } ?: any

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            ) {
                Text(labelFor(value))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BITRATE_OPTIONS_KBPS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labelFor(option)) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** Two ASCII letters, upper-cased; anything else typed is dropped. */
internal fun sanitizeCountryCode(input: String): String =
    input.filter { it in 'a'..'z' || it in 'A'..'Z' }.take(2).uppercase()

/**
 * ShoutKit's row subtitle: "Genre · 128 kbps" — the first tag capitalized, the bitrate
 * only when the directory reported one. Empty when neither is known.
 */
internal fun Station.browseSubtitle(kbpsLabel: (Int) -> String): String =
    listOfNotNull(
        tagList.firstOrNull()?.let { Tag(it).displayName },
        bitrate.takeIf { it > 0 }?.let(kbpsLabel)
    ).joinToString(" · ")

/** "Filters: min 128 kbps, tag jazz, country US" for the empty state. */
internal fun filterSummary(resources: Resources, filters: StationSearchFilters): String {
    val parts = listOfNotNull(
        filters.bitrateMinKbps?.let { resources.getString(R.string.filter_summary_min, it) },
        filters.bitrateMaxKbps?.let { resources.getString(R.string.filter_summary_max, it) },
        filters.tag?.let { resources.getString(R.string.filter_summary_tag, it) },
        filters.countryCode?.let { resources.getString(R.string.filter_summary_country, it) }
    )
    return resources.getString(R.string.filter_summary, parts.joinToString(", "))
}
