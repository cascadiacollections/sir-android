package com.cascadiacollections.sir.okhttp.streaming

import android.os.Process
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.toHttpUrlOrNull

/**
 * Opens short-lived requests to likely stream hosts so their DNS and connection-pool entries
 * are ready for playback. Callers opt in explicitly; this class never prewarms by default.
 */
class StationConnectionPrewarmer(
    private val client: OkHttpClient,
    private val isPowerSaveMode: () -> Boolean,
    private val execute: ((() -> Unit) -> Unit) = ::runInBackground
) {

    fun prewarm(streamUrls: Iterable<String>) {
        if (isPowerSaveMode()) return

        val urls = streamUrls.asSequence()
            .mapNotNull { it.toHttpUrlOrNull() }
            .distinctBy { "${it.scheme}://${it.host}:${it.port}" }
            .take(MAX_HOSTS)
            .toList()
        if (urls.isEmpty()) return

        execute {
            urls.forEach { url ->
                runCatching {
                    client.newCall(Request.Builder().url(url).head().build()).execute().use { }
                }
            }
        }
    }

    private companion object {
        const val MAX_HOSTS = 2

        fun runInBackground(task: () -> Unit) {
            Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task()
            }.start()
        }
    }
}
