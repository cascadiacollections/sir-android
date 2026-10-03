package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.StationIds
import com.cascadiacollections.sir.core.model.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Reports each explicit station play to radio-browser, as its API guidance asks.
 *
 * Driven by [selections] — in production `SettingsRepository.stationSelections`, which
 * fires only when a user picks a station, from whichever surface — so resumes,
 * reconnects and retries are never counted. Fire-and-forget: each report runs in its
 * own child coroutine and any failure is swallowed, because popularity accounting must
 * never affect playback.
 */
class StationPlayReporter(
    private val selections: Flow<Station>,
    private val isEnabled: suspend () -> Boolean,
    private val directory: () -> RadioDirectory
) {

    /**
     * Starts collecting in [scope]. Subscribes before returning, so a selection made
     * immediately after this call is not missed.
     */
    fun start(scope: CoroutineScope): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        selections.collect { station ->
            // Bundled and imported stations have no radio-browser id; skip them before
            // touching settings or the network.
            if (!StationIds.isRadioBrowserUuid(station.id)) return@collect
            launch {
                runCatching {
                    if (isEnabled()) directory().reportClick(station.id)
                }
            }
        }
    }
}
