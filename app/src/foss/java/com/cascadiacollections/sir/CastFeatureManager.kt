package com.cascadiacollections.sir

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FOSS build: Cast is not offered.
 *
 * The Play implementation drives `SplitInstallManager`, which is part of the proprietary
 * Play Core library and can only deliver a module through the Play Store. Shipping it in
 * the FOSS variant put a non-free dependency in the APK and gave FOSS users a Chromecast
 * toggle whose install could never succeed.
 *
 * This implementation reports [CastModuleState.Unavailable] permanently, which is the
 * signal the settings screen uses to omit the Chromecast row altogether. The API mirrors
 * `src/play/.../CastFeatureManager.kt` exactly so shared code in `main` compiles against
 * either flavor unchanged; the mutating entry points are deliberate no-ops rather than
 * throwing, because callers in `main` are flavor-agnostic by design.
 */
@Suppress("UNUSED_PARAMETER")
class CastFeatureManager(context: Context) {

    private val _moduleState = MutableStateFlow<CastModuleState>(CastModuleState.Unavailable)
    val moduleState: StateFlow<CastModuleState> = _moduleState.asStateFlow()

    fun isModuleInstalled(): Boolean = false

    fun installCastModule() = Unit

    fun retry() = Unit

    fun release() = Unit

    companion object {
        const val CAST_MODULE_NAME = "cast"
    }
}
