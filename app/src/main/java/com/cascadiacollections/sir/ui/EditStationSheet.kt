package com.cascadiacollections.sir.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.core.model.Station

object EditStationSheetTestTags {
    const val NAME_FIELD = "edit_station_name"
    const val URL_FIELD = "edit_station_url"
    const val ARTWORK_FIELD = "edit_station_artwork"
    const val SAVE_BUTTON = "edit_station_save"
    const val CANCEL_BUTTON = "edit_station_cancel"
}

/**
 * Bottom sheet for renaming a saved station or correcting its stream/artwork URL.
 *
 * State is keyed to [station.id] so switching which station is being edited (the sheet
 * is recreated per long-press) resets the fields instead of carrying over stale edits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStationSheet(
    station: Station,
    onDismiss: () -> Unit,
    onSave: (Station) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(station.id) { mutableStateOf(station.name) }
    var url by remember(station.id) { mutableStateOf(station.url) }
    var artworkUrl by remember(station.id) { mutableStateOf(station.favicon.orEmpty()) }

    val urlValid = url.startsWith("http://") || url.startsWith("https://")
    val canSave = name.isNotBlank() && urlValid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(stringResource(R.string.edit_station), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.station_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EditStationSheetTestTags.NAME_FIELD)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.station_url_label)) },
                singleLine = true,
                isError = !urlValid,
                supportingText = if (!urlValid) {
                    { Text(stringResource(R.string.station_url_invalid)) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EditStationSheetTestTags.URL_FIELD)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = artworkUrl,
                onValueChange = { artworkUrl = it },
                label = { Text(stringResource(R.string.station_artwork_url_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EditStationSheetTestTags.ARTWORK_FIELD)
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(EditStationSheetTestTags.CANCEL_BUTTON)
                ) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    onClick = {
                        onSave(
                            station.copy(
                                name = name.trim(),
                                url = url.trim(),
                                favicon = artworkUrl.trim().ifBlank { null }
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.testTag(EditStationSheetTestTags.SAVE_BUTTON)
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
