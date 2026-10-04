package com.cascadiacollections.sir.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cascadiacollections.sir.AutomationLinks
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.StationShortcuts
import com.cascadiacollections.sir.core.model.Station

/**
 * A station's automation actions — "Copy automation link" and, where the launcher can pin
 * shortcuts, "Add to home screen" — for the long-press menus and the edit sheet. Empty for
 * a station with nothing to play.
 */
@Composable
internal fun stationAutomationActions(station: Station): List<TileAction> {
    val context = LocalContext.current
    val canPin = remember(context) { StationShortcuts.canPin(context) }
    val copyLabel = stringResource(R.string.copy_automation_link)
    val pinLabel = stringResource(R.string.add_to_home_screen)
    if (!station.isPlayable) return emptyList()
    return listOfNotNull(
        TileAction(copyLabel) { AutomationLinks.copy(context, station) },
        TileAction(pinLabel) { StationShortcuts.requestPin(context, station) }.takeIf { canPin }
    )
}

/** A long-press [DropdownMenu] listing [actions]; each closes the menu before it runs. */
@Composable
internal fun StationActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<TileAction>,
    modifier: Modifier = Modifier
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                onClick = {
                    onDismiss()
                    action.onClick()
                }
            )
        }
    }
}
