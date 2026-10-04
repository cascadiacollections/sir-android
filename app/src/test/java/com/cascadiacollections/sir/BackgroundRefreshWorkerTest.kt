package com.cascadiacollections.sir

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundRefreshWorkerTest {

    private val directory = BackgroundRefreshTest.FakeDirectory()
    private val favorites = BackgroundRefreshTest.FakeFavorites(
        listOf(BackgroundRefreshTest.station(BackgroundRefreshTest.UUID_A))
    )

    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            BackgroundRefreshWorker(appContext, workerParameters) {
                BackgroundRefresh(directory, favorites)
            }
    }

    private fun worker(runAttemptCount: Int = 0) =
        TestListenableWorkerBuilder<BackgroundRefreshWorker>(RuntimeEnvironment.getApplication())
            .setWorkerFactory(factory)
            .setRunAttemptCount(runAttemptCount)
            .build()

    @Test
    fun `a run refreshes discovery and saved stations`() = runBlocking {
        val result = worker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(directory.topCalls).hasSize(1)
        assertThat(directory.lookups).containsExactly(listOf(BackgroundRefreshTest.UUID_A))
        assertThat(favorites.savedStations.value.single().urlResolved)
            .isEqualTo("https://fresh.example/${BackgroundRefreshTest.UUID_A}")
        assertThat(directory.clicks).isEmpty()
    }

    @Test
    fun `a total failure retries, then gives up until the next period`() = runBlocking {
        directory.top = Result.failure(IOException("offline"))
        directory.tags = Result.failure(IOException("offline"))
        directory.lookup = Result.failure(IOException("offline"))

        assertThat(worker(runAttemptCount = 0).doWork()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(worker(runAttemptCount = BackgroundRefreshWorker.MAX_RETRIES).doWork())
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `the periodic request runs every 4 hours on unmetered networks with battery not low`() {
        val spec = BackgroundRefreshWorker.request().workSpec

        assertThat(spec.intervalDuration).isEqualTo(TimeUnit.HOURS.toMillis(4))
        assertThat(spec.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
        assertThat(spec.constraints.requiresBatteryNotLow()).isTrue()
        assertThat(spec.workerClassName).isEqualTo(BackgroundRefreshWorker::class.java.name)
    }
}
