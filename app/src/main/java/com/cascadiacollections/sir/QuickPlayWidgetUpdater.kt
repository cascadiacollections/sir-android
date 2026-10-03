package com.cascadiacollections.sir

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "QuickPlayWidgetUpdater"

/**
 * Keeps the Quick Play widget in step with playback.
 *
 * Glance renders a widget once per session and then leaves a static RemoteViews behind, so
 * something has to ask it to re-render when what it shows changes: whether audio is playing,
 * the current selection, or the favourites it picks from. Those are combined, de-duplicated
 * and debounced, so a burst (a station switch is a selection write followed by a pause/play
 * pair) costs one `updateAll` rather than several.
 *
 * Runs only while at least one widget exists — started by [SirApp] when one is already
 * placed and by [RadioWidgetReceiver] when one is added — so a user without the widget
 * pays nothing.
 */
object QuickPlayWidgetUpdater {

    private const val DEBOUNCE_MS = 300L

    private val playing = MutableStateFlow(false)

    /**
     * Whether the playback service is playing, published by the service itself. In-process
     * only: when the process dies the service died with it, so `false` is then correct.
     */
    val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** The service's single hook: call from its player's `onIsPlayingChanged`. */
    fun onPlaybackChanged(isPlaying: Boolean) {
        playing.value = isPlaying
    }

    /** Starts the updater if a Quick Play widget is placed; a no-op for everyone else. */
    fun startIfWidgetsPlaced(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val ids = runCatching {
                AppWidgetManager.getInstance(app)
                    ?.getAppWidgetIds(ComponentName(app, RadioWidgetReceiver::class.java))
            }.getOrNull()
            if (ids != null && ids.isNotEmpty()) start(app)
        }
    }

    @OptIn(FlowPreview::class)
    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        job = scope.launch {
            combine(settings.savedStations, settings.selectedStation, playing) { saved, selected, isPlaying ->
                Triple(saved, selected?.id, isPlaying)
            }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MS)
                .collect {
                    try {
                        RadioWidget().updateAll(app)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Quick Play widget refresh failed", e)
                    }
                }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }
}
