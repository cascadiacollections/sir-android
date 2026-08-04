package com.cascadiacollections.sir

/**
 * State of the Cast dynamic feature module.
 *
 * Lives in `main` because it carries no Play dependency: both the Play and FOSS
 * implementations of [CastFeatureManager] report through this type, and the settings UI
 * renders from it without knowing which flavor it is running in.
 */
sealed interface CastModuleState {
    data object NotInstalled : CastModuleState
    data class Installing(val progress: Float) : CastModuleState
    data object Installed : CastModuleState
    data class Failed(val errorCode: Int) : CastModuleState

    /**
     * Cast cannot exist in this build at all — the FOSS flavor ships no Play delivery
     * client, so there is no module to install and no error to retry.
     *
     * Distinct from [Failed] on purpose: a failure invites a retry, whereas this is a
     * permanent property of the build. The settings screen omits the Chromecast row
     * entirely rather than showing a control that cannot work.
     */
    data object Unavailable : CastModuleState
}
