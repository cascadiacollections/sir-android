package com.cascadiacollections.sir

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundRefreshWorkerTest {

    private val directory = BackgroundRefreshTest.FakeDirectory()
    private val favorites = BackgroundRefreshTest.FakeFavorites(listOf(BackgroundRefreshTest.station(BackgroundRefreshTest.UUID_A)))

    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            BackgroundRefreshWorker(appContext, workerParameters) { BackgroundRefresh(directory, favorites) }
    }

    private fun worker(runAttemptCount: Int = 0) =
        TestListenableWorkerBuilder<BackgroundRefreshWorker>(RuntimeEnvironment.getApplication())
            .setWorkerFactory(factory)
            .setRunAttemptCount(runAttemptCount)
            .build()

    @Test
    fun `a run refreshes discovery and saved stations`() = runBlocking {
        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, directory.topCalls.size)
        assertEquals(listOf(listOf(BackgroundRefreshTest.UUID_A)), directory.lookups)
        assertEquals(
            "https://fresh.example/${BackgroundRefreshTest.UUID_A}",
            favorites.savedStations.value.single().urlResolved
        )
        assertTrue(directory.clicks.isEmpty())
    }

    @Test
    fun `a total failure retries, then gives up until the next period`() = runBlocking {
        directory.top = Result.failure(IOException("offline"))
        directory.tags = Result.failure(IOException("offline"))
        directory.lookup = Result.failure(IOException("offline"))

        assertEquals(ListenableWorker.Result.retry(), worker(runAttemptCount = 0).doWork())
        assertEquals(
            ListenableWorker.Result.success(),
            worker(runAttemptCount = BackgroundRefreshWorker.MAX_RETRIES).doWork()
        )
    }

    @Test
    fun `the periodic request runs every 4 hours on unmetered networks with battery not low`() {
        val spec = BackgroundRefreshWorker.request().workSpec

        assertEquals(TimeUnit.HOURS.toMillis(4), spec.intervalDuration)
        assertEquals(NetworkType.UNMETERED, spec.constraints.requiredNetworkType)
        assertTrue(spec.constraints.requiresBatteryNotLow())
        assertEquals(BackgroundRefreshWorker::class.java.name, spec.workerClassName)
    }
}
