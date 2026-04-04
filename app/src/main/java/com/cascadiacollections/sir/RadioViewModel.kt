package com.cascadiacollections.sir

import android.app.Application
import android.content.ComponentName
import android.net.ConnectivityManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

class RadioViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
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
            _uiState.update {
                it.copy(
                    isPlaying = player.isActuallyPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING,
                    isError = if (player.playbackState == Player.STATE_READY) false else it.isError
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update { it.copy(isError = true) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()
            val artist = mediaMetadata.artist?.toString()
            _uiState.update { it.copy(trackTitle = title, artist = artist) }
            title?.let {
                viewModelScope.launch { settingsRepository.addHistoryEntry(it, artist) }
            }
        }
    }

    init {
        connectMediaController()
        checkMeteredNetwork()
        observeSleepTimer()
    }

    private fun connectMediaController() {
        viewModelScope.launch {
            getApplication<Application>().ensureRadioServiceRunning()
            try {
                val newController = MediaController.Builder(getApplication(), sessionToken)
                    .buildAsync()
                    .await()
                controller = newController
                newController.addListener(listener)
                _uiState.update {
                    it.copy(
                        isConnected = true,
                        isPlaying = newController.isActuallyPlaying,
                        isBuffering = newController.playbackState == Player.STATE_BUFFERING,
                        trackTitle = newController.mediaMetadata.title?.toString(),
                        artist = newController.mediaMetadata.artist?.toString()
                    )
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

    private fun observeSleepTimer() {
        viewModelScope.launch {
            settingsRepository.sleepTimerFiresAt.collect { firesAt ->
                while (true) {
                    val remaining = firesAt - System.currentTimeMillis()
                    _uiState.update {
                        it.copy(
                            sleepTimerLabel = if (remaining > 0)
                                getApplication<Application>().getString(
                                    R.string.sleep_timer_countdown,
                                    (remaining / 60_000).toInt().coerceAtLeast(1)
                                ) else null
                        )
                    }
                    if (firesAt <= 0L || remaining <= 0L) break
                    delay(30_000L)
                }
            }
        }
    }

    fun togglePlayback() {
        val activeController = controller
        if (activeController == null) {
            getApplication<Application>().ensureRadioServiceRunning()
            return
        }
        if (_uiState.value.isPlaying) {
            activeController.pause()
        } else {
            getApplication<Application>().ensureRadioServiceRunning()
            activeController.play()
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
