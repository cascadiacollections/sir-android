package com.cascadiacollections.sir

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.WorkManager
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.okhttp.streaming.StationConnectionPrewarmer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SirApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Before anything can touch AppDirectory.instance, so the chain gets its snapshot.
        AppDirectory.install(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        // One central hook for "the user started a station", covering every entry point.
        StationPlayReporter(
            selections = SettingsRepository.stationSelections,
            isEnabled = { SettingsRepository(applicationContext).reportPlaysToDirectory.first() },
            directory = { AppDirectory.instance }
        ).start(applicationScope)
        // Re-renders the Quick Play widget as playback changes; idle unless one is placed.
        QuickPlayWidgetUpdater.startIfWidgetsPlaced(this)
        // Mirrors the last-played station and recents to the Wear app (Play only; a no-op
        // in the FOSS flavor, which has no Data Layer).
        WearStationPublisher.start(this, applicationScope)
        applicationScope.launch {
            try {
                BackgroundRefreshWorker.schedule(WorkManager.getInstance(this@SirApp))
            } catch (e: IllegalStateException) {
                // WorkManager is initialised by its startup provider; a host that skips
                // providers (Robolectric) has none, and that must not take the app down.
                Log.w("SirApp", "Background refresh not scheduled", e)
            }
        }
        applicationScope.launch {
            val settings = SettingsRepository(applicationContext)
            if (settings.connectionPrewarmingEnabled.first()) {
                StationConnectionPrewarmer(
                    client = StreamingHttpClientProvider.client,
                    isPowerSaveMode = {
                        getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
                    }
                ).prewarm(settings.mostPlayedSavedStations.first().map { it.streamUrl })
            }
        }
    }
}
