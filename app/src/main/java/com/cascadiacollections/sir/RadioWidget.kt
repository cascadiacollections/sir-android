package com.cascadiacollections.sir

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cascadiacollections.sir.core.persistence.QuickPlaySelection
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.ui.theme.Amber40
import com.cascadiacollections.sir.ui.theme.Amber80
import com.cascadiacollections.sir.ui.theme.AmberGrey40
import com.cascadiacollections.sir.ui.theme.AmberGrey80
import com.cascadiacollections.sir.ui.theme.Coral40
import com.cascadiacollections.sir.ui.theme.Coral80
import kotlinx.coroutines.flow.first

private const val TAG = "RadioWidget"

/**
 * The Quick Play home-screen widget (ShoutKit's "Quick Play"): one favourite station and a
 * play/pause button.
 *
 * Each widget instance plays the station pinned in [QuickPlayConfigActivity] — stored in
 * that instance's own Glance state under [PINNED_STATION_ID] — or the first saved station
 * until one is pinned. See [QuickPlaySelection] for what a tap does.
 *
 * The content collects its inputs as state, so it stays live for as long as Glance keeps the
 * session open; [QuickPlayWidgetUpdater] re-renders it after that, when playback, the
 * selection or the favourites change.
 */
class RadioWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        /** Per-widget Glance state: the id of the saved station this widget plays. */
        val PINNED_STATION_ID: Preferences.Key<String> = stringPreferencesKey("quick_play_station_id")

        private val staticColors = ColorProviders(
            light = lightColorScheme(
                primary = Amber40,
                secondary = AmberGrey40,
                tertiary = Coral40
            ),
            dark = darkColorScheme(
                primary = Amber80,
                secondary = AmberGrey80,
                tertiary = Coral80
            )
        )

        // Dynamic color depends on the runtime wallpaper, so it can't be a compile-time
        // constant like staticColors; fall back to the static brand palette below API 31.
        private fun colorsFor(context: Context) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ColorProviders(
                    light = dynamicLightColorScheme(context),
                    dark = dynamicDarkColorScheme(context)
                )
            } else {
                staticColors
            }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // provideGlance is suspending, so the persisted state is read up front as the
        // initial values; nothing assumes the SIR stream is what is playing.
        val settings = SettingsRepository(context.applicationContext)
        val initialSaved = settings.savedStations.first()
        val initialSelected = settings.selectedStation.first()
        val defaultName = context.getString(R.string.station_name)

        provideContent {
            val saved by settings.savedStations.collectAsState(initialSaved)
            val selected by settings.selectedStation.collectAsState(initialSelected)
            val isPlaying by QuickPlayWidgetUpdater.isPlaying.collectAsState()
            val pinnedId = currentState(PINNED_STATION_ID)

            val station = QuickPlaySelection.resolveStation(saved, pinnedId)
            val name = (station ?: selected)?.name?.takeIf { it.isNotBlank() } ?: defaultName
            val showPause = QuickPlaySelection.showsPause(station, selected, isPlaying)

            GlanceTheme(colors = colorsFor(context)) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(40.dp)
                            .cornerRadius(20.dp)
                            .background(GlanceTheme.colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = QuickPlaySelection.initials(name),
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimaryContainer,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(
                        text = name,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 18.sp
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Image(
                        provider = ImageProvider(if (showPause) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = context.getString(if (showPause) R.string.pause else R.string.play),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                        modifier = GlanceModifier
                            .size(40.dp)
                            .clickable(actionRunCallback<TogglePlaybackAction>())
                    )
                }
            }
        }
    }
}

/**
 * The widget's play button. Suspends rather than blocking on a controller future: the
 * decision needs only persisted state plus the in-process playing flag, and the command
 * is delivered to the service as an intent.
 */
class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val settings = SettingsRepository(context.applicationContext)
        val pinnedId = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[RadioWidget.PINNED_STATION_ID]
        val station = QuickPlaySelection.resolveStation(settings.savedStations.first(), pinnedId)
        val isPlaying = QuickPlayWidgetUpdater.isPlaying.value

        when (val action = QuickPlaySelection.tapAction(station, settings.selectedStation.first(), isPlaying)) {
            is QuickPlaySelection.TapAction.SelectAndPlay -> {
                settings.selectStation(action.station)
                // While something plays the service is alive, and its selection collector
                // starts the new station itself. Otherwise it may be cold, where only an
                // explicit play starts audio.
                if (!isPlaying) context.sendPlaybackCommand(RadioPlaybackService.ACTION_PLAY)
            }
            QuickPlaySelection.TapAction.Play -> context.sendPlaybackCommand(RadioPlaybackService.ACTION_PLAY)
            QuickPlaySelection.TapAction.Pause -> context.sendPlaybackCommand(RadioPlaybackService.ACTION_PAUSE)
        }
    }

    private fun Context.sendPlaybackCommand(action: String) {
        val intent = Intent(this, RadioPlaybackService::class.java).setAction(action)
        try {
            if (action == RadioPlaybackService.ACTION_PLAY) {
                // A widget tap is a user interaction, which exempts this foreground start.
                ContextCompat.startForegroundService(this, intent)
            } else {
                // Pause only follows playback, so the service is already in the foreground;
                // a foreground start here would oblige it to post a notification it may not.
                startService(intent)
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Widget could not reach the playback service", e)
        }
    }
}

class RadioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RadioWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        QuickPlayWidgetUpdater.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        QuickPlayWidgetUpdater.stop()
    }
}
