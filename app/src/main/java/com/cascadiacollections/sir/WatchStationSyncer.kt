package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.WatchStationSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Turns the phone's selection and recents into the watch payload and hands each distinct,
 * settled value to [publish] — the transport is the caller's (the Wearable Data Layer in
 * the Play flavor, see `WearStationPublisher`).
 *
 * Selecting a station writes the selection and the recents in one DataStore transaction,
 * but the two flows still emit separately; the debounce coalesces such a burst into one
 * write, and `distinctUntilChanged` drops writes that would not change what the watch
 * shows. A failed publish is dropped: the next change re-sends the whole state anyway.
 */
class WatchStationSyncer(
    private val selected: Flow<Station?>,
    private val recents: Flow<List<Station>>,
    private val publish: suspend (String) -> Unit,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS
) {

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(selected, recents) { last, recent -> WatchStationSync.payload(last, recent) }
            .distinctUntilChanged()
            .debounce(debounceMs)
            .collect { payload ->
                try {
                    publish(WatchStationSync.encode(payload))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Sync is best effort; it must never take the app scope down.
                }
            }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS: Long = 1_000L
    }
}
