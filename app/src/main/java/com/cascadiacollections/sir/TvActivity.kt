package com.cascadiacollections.sir

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.core.persistence.TrackHistoryRepository
import com.cascadiacollections.sir.ui.TvHomeScreen
import com.cascadiacollections.sir.ui.TvPopular
import com.cascadiacollections.sir.ui.theme.SirTheme
import kotlinx.coroutines.launch

/**
 * The Android TV entry point (LEANBACK_LAUNCHER), ShoutKit's tvOS app: one D-pad-driven
 * screen with a now-playing banner over Recent and Popular station shelves.
 *
 * Playback, recents and the directory are the phone app's own — the same service, view
 * models and [AppDirectory] — so the TV only adds a layout built for the remote. Entry
 * points that only [MainActivity] declares (voice search, `sir://station` links) are
 * forwarded here on a TV; see [forward].
 */
class TvActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) playLinkedStation(intent)
        setContent {
            SirTheme(darkTheme = true) {
                val radio: RadioViewModel = viewModel(
                    factory = RadioViewModel.Factory(
                        application = applicationContext as Application,
                        settingsRepository = settingsRepository
                    )
                )
                val browser: RadioBrowserViewModel = viewModel(
                    factory = RadioBrowserViewModel.Factory(
                        directory = AppDirectory.instance,
                        settingsRepository = settingsRepository,
                        trackHistoryRepository = TrackHistoryRepository(applicationContext)
                    )
                )
                val radioState by radio.uiState.collectAsState()
                val browserState by browser.uiState.collectAsState()
                // Bumped by Retry; keys the load so a failed shelf can be fetched again.
                var popularAttempt by rememberSaveable { mutableIntStateOf(0) }
                val popular by produceState<TvPopular>(TvPopular.Loading, popularAttempt) {
                    value = TvPopular.Loading
                    value = AppDirectory.instance.topStations(POPULAR_LIMIT).fold(
                        onSuccess = { TvPopular.Loaded(it) },
                        onFailure = { TvPopular.Failed }
                    )
                }

                TvHomeScreen(
                    radio = radioState,
                    recents = browserState.recentStations.take(RECENT_LIMIT),
                    popular = popular,
                    onStationSelected = { station: Station ->
                        // As on tvOS, choosing the station already playing toggles it.
                        if (station.id == radioState.station?.id) {
                            radio.togglePlayback()
                        } else {
                            browser.playStation(
                                station
                            )
                        }
                    },
                    onTogglePlayback = radio::togglePlayback,
                    onStop = radio::stop,
                    onRetryPopular = { popularAttempt++ }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        playLinkedStation(intent)
    }

    private fun playLinkedStation(intent: Intent?) {
        val id = StationDeepLink.stationId(intent) ?: return
        lifecycleScope.launch {
            StationDeepLink.play(id, AppDirectory.instance, settingsRepository)
        }
    }

    companion object {
        // ShoutKit's TVRootView: ten recents, thirty popular stations.
        private const val RECENT_LIMIT = 10
        private const val POPULAR_LIMIT = 30

        /** Whether this device is a TV, where [MainActivity] hands over to this activity. */
        fun isTelevision(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_TELEVISION

        /** [original]'s link (if any) re-addressed to the TV home. */
        fun forward(context: Context, original: Intent?): Intent = Intent(context, TvActivity::class.java).apply {
            action = original?.action
            data = original?.data
        }
    }
}
