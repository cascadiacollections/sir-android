package com.cascadiacollections.sir

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.ui.theme.SirTheme
import kotlinx.coroutines.launch

private const val TAG = "QuickPlayConfig"

/**
 * Picks which saved station a Quick Play widget plays. Shown when the widget is placed and,
 * on launchers that support it, from the widget's long-press "reconfigure" action.
 *
 * "First saved station" stores nothing, so the widget keeps following whatever is first in
 * the user's favourites — ShoutKit's default.
 */
class QuickPlayConfigActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out must not place a half-configured widget.
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val glanceManager = GlanceAppWidgetManager(this)

        enableEdgeToEdge()
        setContent {
            SirTheme {
                val saved by settingsRepository.savedStations.collectAsState(initial = emptyList())
                val pinnedId by produceState<String?>(initialValue = null, appWidgetId) {
                    value = runCatching {
                        val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                        getAppWidgetState(this@QuickPlayConfigActivity, PreferencesGlanceStateDefinition, glanceId)[
                            RadioWidget.PINNED_STATION_ID
                        ]
                    }.getOrNull()
                }
                QuickPlayPicker(
                    stations = saved,
                    pinnedId = pinnedId,
                    onPick = { station -> pin(appWidgetId, station?.id) }
                )
            }
        }
    }

    private fun pin(appWidgetId: Int, stationId: String?) {
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(this@QuickPlayConfigActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@QuickPlayConfigActivity, glanceId) { prefs ->
                    if (stationId == null) prefs.remove(RadioWidget.PINNED_STATION_ID)
                    else prefs[RadioWidget.PINNED_STATION_ID] = stationId
                }
                RadioWidget().update(this@QuickPlayConfigActivity, glanceId)
                QuickPlayWidgetUpdater.start(this@QuickPlayConfigActivity)
                setResult(RESULT_OK, resultIntent(appWidgetId))
            } catch (e: IllegalArgumentException) {
                // The id is not one of ours (a stale or forged configure intent).
                Log.w(TAG, "No Quick Play widget with id $appWidgetId", e)
            }
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickPlayPicker(
    stations: List<Station>,
    pinnedId: String?,
    onPick: (Station?) -> Unit
) {
    // A pinned id that is no longer saved behaves like the default, so show it that way.
    val effectivePinned = pinnedId?.takeIf { id -> stations.any { it.id == id } }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.quick_play_choose_station)) }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                QuickPlayOption(
                    label = stringResource(R.string.quick_play_first_saved),
                    selected = effectivePinned == null,
                    onClick = { onPick(null) }
                )
            }
            if (stations.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.quick_play_no_saved_stations),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(stations, key = { it.id }) { station ->
                QuickPlayOption(
                    label = station.name,
                    selected = station.id == effectivePinned,
                    onClick = { onPick(station) }
                )
            }
        }
    }
}

@Composable
private fun QuickPlayOption(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
