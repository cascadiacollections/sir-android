package com.cascadiacollections.sir

import android.app.Application
import android.os.StrictMode
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
        applicationScope.launch {
            val settings = SettingsRepository(applicationContext)
            if (settings.connectionPrewarmingEnabled.first()) {
                StationConnectionPrewarmer(
                    client = StreamingHttpClientProvider.client,
                    isPowerSaveMode = {
                        getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
                    }
                ).prewarm(settings.mostPlayedSavedStations.first().map { it.url })
            }
        }
    }
}
