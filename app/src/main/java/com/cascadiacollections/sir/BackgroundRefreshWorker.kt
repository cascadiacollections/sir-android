package com.cascadiacollections.sir

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Runs [BackgroundRefresh] every [REPEAT_HOURS] hours (ShoutKit's 4 h cadence) so the
 * browse tab's snapshot and saved stations' streams are current the next time the app
 * opens. Unmetered networks only, as ShoutKit skips prefetch on cellular and Low Data
 * Mode, and never on a low battery.
 */
class BackgroundRefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val refresh: () -> BackgroundRefresh
) : CoroutineWorker(context, params) {

    /** The constructor WorkManager's default factory instantiates. */
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        { BackgroundRefresh(AppDirectory.instance, SettingsRepository(context.applicationContext)) }
    )

    override suspend fun doWork(): Result {
        val outcome = refresh().run()
        if (BuildConfig.DEBUG) Log.d(TAG, "Background refresh: $outcome")
        return when {
            !outcome.shouldRetry -> Result.success()
            // Give up until the next period rather than retrying an outage all day.
            runAttemptCount >= MAX_RETRIES -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "BackgroundRefresh"
        const val UNIQUE_WORK_NAME: String = "directory-background-refresh"
        const val REPEAT_HOURS: Long = 4
        const val MAX_RETRIES: Int = 3

        val constraints: Constraints
            get() = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

        fun request(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<BackgroundRefreshWorker>(REPEAT_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

        /** Idempotent: KEEP leaves an already-scheduled refresh (and its cadence) alone. */
        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request())
        }
    }
}
