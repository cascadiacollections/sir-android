package com.cascadiacollections.sir

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.core.persistence.TrackHistoryRepository
import com.cascadiacollections.sir.ui.TvHomeScreen
import com.cascadiacollections.sir.ui.TvPopular
import com.cascadiacollections.sir.ui.theme.SirTheme

/**
 * The Android TV entry point (LEANBACK_LAUNCHER), ShoutKit's tvOS app: one D-pad-driven
 * screen with a now-playing banner over Recent and Popular station shelves.
 *
 * Playback, recents and the directory are the phone app's own — the same service, view
 * models and [AppDirectory] — so the TV only adds a layout built for the remote.
 */
class TvActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                val popular by produceState<TvPopular>(TvPopular.Loading) {
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
                        if (station.id == radioState.station?.id) radio.togglePlayback() else browser.playStation(station)
                    },
                    onTogglePlayback = radio::togglePlayback,
                    onStop = radio::stop
                )
            }
        }
    }

    private companion object {
        // ShoutKit's TVRootView: ten recents, thirty popular stations.
        const val RECENT_LIMIT = 10
        const val POPULAR_LIMIT = 30
    }
}
