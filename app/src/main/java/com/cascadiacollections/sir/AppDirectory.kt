package com.cascadiacollections.sir

import android.content.Context
import com.cascadiacollections.sir.core.directory.FileDiscoverySnapshotStore
import com.cascadiacollections.sir.core.directory.RadioDirectories
import com.cascadiacollections.sir.core.directory.RadioDirectory
import java.io.File

/**
 * Process-wide [RadioDirectory].
 *
 * The chain owns an [okhttp3.OkHttpClient] (connection pool plus dispatcher threads) and
 * an in-memory response cache, so it must outlive any single Activity. Building it per
 * composition would both leak thread pools across configuration changes and throw the
 * cache away on exactly the rotation and back-navigation cases it exists to serve.
 *
 * [install] (called first thing in [SirApp.onCreate]) gives it a place for the on-disk
 * discovery snapshot. Without it — e.g. a test that never created the application — the
 * chain simply has no snapshot layer. Only the application context is retained, so this
 * is safe as a static singleton.
 */
object AppDirectory {

    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    val instance: RadioDirectory by lazy {
        RadioDirectories.create(
            snapshotStore = appContext?.let { context ->
                // Resolved lazily (on first use, off the main thread's critical path) because
                // noBackupFilesDir may create the directory. No-backup: it is a cache that a
                // restore onto another device would only make stale.
                FileDiscoverySnapshotStore(File(context.noBackupFilesDir, FileDiscoverySnapshotStore.FILE_NAME))
            }
        )
    }
}
