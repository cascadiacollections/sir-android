package com.cascadiacollections.sir

import android.app.Application
import android.content.Intent
import android.content.ComponentName
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.persistence.HeardTrack
import com.cascadiacollections.sir.core.persistence.HeardTracks
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.core.persistence.TrackHistoryRepository
import com.cascadiacollections.sir.core.playback.StreamFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val TAG = "RadioViewModel"

data class RadioUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isError: Boolean = false,
    val trackTitle: String? = null,
    val artist: String? = null,
    val sleepTimerLabel: String? = null,
    val showMeteredWarning: Boolean = false,
    // Appended last, after showMeteredWarning, to avoid shifting the componentN()
    // destructuring order other tests/call sites may rely on for the fields above.
    /** The most recent persisted Recently Heard tracks, newest first. */
    val trackHistory: List<HeardTrack> = emptyList(),
    /** The selected directory/saved station; null for the app's own stream. */
    val station: Station? = null,
    /** Whether [station] is in the user's favourites. */
    val isFavorite: Boolean = false,
    /** Why the stream stopped, as classified by the service; null while healthy. */
    val failure: StreamFailure? = null,
    /** Whether the service has a reconnect attempt scheduled or in flight for [failure]. */
    val isReconnecting: Boolean = false,
    /** Cover art for the current track, when the iTunes lookup found some. */
    val albumArtUrl: String? = null,
    /** The current track's Apple Music page, when the iTunes lookup found one. */
    val trackViewUrl: String? = null,
    /**
     * Whether the listener has asked to hear the stream (the player's `playWhenReady`). True
     * until the controller says otherwise, so a state built without a player reads as before.
     * A failure or a buffering spell is only shown while this is true.
     */
    val isPlayRequested: Boolean = true,
)

class RadioViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val trackHistoryRepository: TrackHistoryRepository = TrackHistoryRepository(application),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null

    private val sessionToken = SessionToken(
        application,
        ComponentName(application, RadioPlaybackService::class.java)
    )

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _uiState.update { it.withPlayer(player) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update { it.withPlayerError(playWhenReady = controller?.playWhenReady == true) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Track history is recorded by RadioPlaybackService, which sees every resolved
            // track whether or not this UI is alive; here we only mirror what's on screen.
            _uiState.update { it.withMediaMetadata(mediaMetadata) }
        }
    }

    // The service publishes its typed StreamFailure as session extras (PlaybackFailureExtras).
    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            _uiState.update { it.withSessionExtras(extras) }
        }
    }

    init {
        connectMediaController()
        checkMeteredNetwork()
        observeSleepTimer()
        observeTrackHistory()
        observeStation()
    }

    private fun observeStation() {
        viewModelScope.launch {
            combine(settingsRepository.selectedStation, settingsRepository.savedStations) { station, saved ->
                station to (station != null && saved.any { it.id == station.id })
            }.collect { (station, isFavorite) ->
                _uiState.update { it.copy(station = station, isFavorite = isFavorite) }
            }
        }
    }

    private fun observeTrackHistory() {
        viewModelScope.launch {
            trackHistoryRepository.tracks.collect { tracks ->
                _uiState.update { it.copy(trackHistory = tracks.take(HeardTracks.SHEET_LIMIT)) }
            }
        }
    }

    private fun connectMediaController() {
        viewModelScope.launch {
            // Binding the controller starts the service; Media3 promotes it to the
            // foreground itself once playback begins. A bare startForegroundService()
            // here obliged the service to call startForeground() within seconds even when
            // nothing played (e.g. a cold start that selects the current station), which
            // Android reports as an ANR.
            try {
                val newController = MediaController.Builder(getApplication(), sessionToken)
                    .setListener(controllerListener)
                    .buildAsync()
                    .await()
                controller = newController
                newController.addListener(listener)
                _uiState.update {
                    it.copy(isConnected = true)
                        .withPlayer(newController)
                        .withMediaMetadata(newController.mediaMetadata)
                        .withSessionExtras(newController.sessionExtras)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Failed to connect to media session", error)
            }
        }
    }

    private fun checkMeteredNetwork() {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        if (cm?.isActiveNetworkMetered == true) {
            _uiState.update { it.copy(showMeteredWarning = true) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSleepTimer() {
        viewModelScope.launch {
            settingsRepository.sleepTimerFiresAt
                .flatMapLatest { firesAt ->
                    if (firesAt <= 0L) {
                        flowOf(null)
                    } else {
                        countdownLabelFlow(firesAt)
                    }
                }
                .collect { label ->
                    _uiState.update { it.copy(sleepTimerLabel = label) }
                }
        }
    }

    private fun countdownLabelFlow(firesAt: Long): Flow<String?> = flow {
        while (true) {
            val remaining = firesAt - System.currentTimeMillis()
            emit(
                if (remaining > 0) {
                    getApplication<Application>().getString(
                        R.string.sleep_timer_countdown,
                        (remaining / 60_000).toInt().coerceAtLeast(1)
                    )
                } else {
                    null
                }
            )
            if (remaining <= 0L) break
            delay(30_000L)
        }
    }

    fun togglePlayback() {
        val activeController = controller
        if (activeController == null) {
            // Not bound yet: ask the service to play directly. A plain startService is
            // allowed because the user just tapped in the foreground activity, and it
            // carries no startForeground() deadline if playback can't begin.
            val app = getApplication<Application>()
            app.startService(
                Intent(app, RadioPlaybackService::class.java)
                    .setAction(RadioPlaybackService.ACTION_PLAY)
            )
            return
        }
        when (_uiState.value.transportAction) {
            TransportAction.PAUSE -> activeController.pause()
            // Pause first so the service treats it as the listener's choice (dismissing
            // the failure and skipping any scheduled reconnect), then drop the connection.
            TransportAction.CANCEL -> {
                activeController.pause()
                activeController.stop()
            }
            TransportAction.PLAY, TransportAction.RETRY -> {
                // A failed, stalled or cancelled player is idle and must be re-prepared.
                if (activeController.playbackState == Player.STATE_IDLE) activeController.prepare()
                activeController.play()
            }
        }
    }

    /** Saves or unsaves the current station. A no-op for the app's own stream. */
    fun toggleFavorite() {
        val state = _uiState.value
        val station = state.station?.takeIf { state.canFavorite } ?: return
        viewModelScope.launch {
            if (state.isFavorite) {
                settingsRepository.removeStation(station.id)
            } else {
                settingsRepository.saveStation(station)
            }
        }
    }

    fun dismissMeteredWarning() {
        _uiState.update { it.copy(showMeteredWarning = false) }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    class Factory(
        private val application: Application,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return RadioViewModel(application, settingsRepository) as T
        }
    }
}
