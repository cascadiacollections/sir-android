package com.cascadiacollections.sir

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State of the Cast dynamic feature module
 */
sealed class CastModuleState {
    data object NotInstalled : CastModuleState()
    data class Installing(val progress: Float) : CastModuleState()
    data object Installed : CastModuleState()
    data class Failed(val errorCode: Int) : CastModuleState()
}

/**
 * Manages the Cast dynamic feature module installation and lifecycle.
 */
class CastFeatureManager(private val context: Context) {

    private val splitInstallManager = SplitInstallManagerFactory.create(context)

    private val _moduleState = MutableStateFlow<CastModuleState>(
        if (isModuleInstalled()) CastModuleState.Installed else CastModuleState.NotInstalled
    )
    val moduleState: StateFlow<CastModuleState> = _moduleState.asStateFlow()

    private var sessionId = 0

    private val stateListener = SplitInstallStateUpdatedListener { state ->
        if (state.sessionId() != sessionId) return@SplitInstallStateUpdatedListener

        when (state.status()) {
            SplitInstallSessionStatus.PENDING,
            SplitInstallSessionStatus.DOWNLOADING -> {
                val progress = if (state.totalBytesToDownload() > 0) {
                    state.bytesDownloaded().toFloat() / state.totalBytesToDownload()
                } else 0f
                _moduleState.value = CastModuleState.Installing(progress)
            }
            SplitInstallSessionStatus.INSTALLING -> {
                _moduleState.value = CastModuleState.Installing(1f)
            }
            SplitInstallSessionStatus.INSTALLED -> {
                _moduleState.value = CastModuleState.Installed
                Log.d(TAG, "Cast module installed successfully")
            }
            SplitInstallSessionStatus.FAILED -> {
                _moduleState.value = CastModuleState.Failed(state.errorCode())
                Log.e(TAG, "Cast module installation failed: ${state.errorCode()}")
            }
            SplitInstallSessionStatus.CANCELED -> {
                _moduleState.value = CastModuleState.NotInstalled
            }
            else -> { /* Ignore other states */ }
        }
    }

    init {
        splitInstallManager.registerListener(stateListener)
    }

    /**
     * Check if the cast module is already installed
     */
    fun isModuleInstalled(): Boolean {
        return splitInstallManager.installedModules.contains(CAST_MODULE_NAME)
    }

    /**
     * Request installation of the cast module
     */
    fun installCastModule() {
        if (isModuleInstalled()) {
            _moduleState.value = CastModuleState.Installed
            return
        }

        if (_moduleState.value is CastModuleState.Installing) {
            return // Already installing
        }

        val request = SplitInstallRequest.newBuilder()
            .addModule(CAST_MODULE_NAME)
            .build()

        _moduleState.value = CastModuleState.Installing(0f)

        splitInstallManager.startInstall(request)
            .addOnSuccessListener { id ->
                sessionId = id
                Log.d(TAG, "Cast module download started, session: $id")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start cast module download", exception)
                _moduleState.value = CastModuleState.Failed(-1)
            }
    }

    /**
     * Retry installation after failure
     */
    fun retry() {
        _moduleState.value = CastModuleState.NotInstalled
        installCastModule()
    }

    fun release() {
        splitInstallManager.unregisterListener(stateListener)
    }

    companion object {
        private const val TAG = "CastFeatureManager"
        const val CAST_MODULE_NAME = "cast"
    }
}

