package com.cascadiacollections.sir.cast

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.media3.common.util.UnstableApi

/**
 * Bootstraps [CastSessionCoordinator] at process start, with no code required in
 * `:app`.
 *
 * `:app` cannot reference `:cast` — dynamic feature modules only depend on the base
 * app, never the reverse — so nothing in `:app` can construct the coordinator
 * directly. A manifest-declared [ContentProvider] is the standard way for a component
 * to self-initialize at process start without any cooperation from the module that
 * contains it (the same mechanism several Jetpack libraries use internally, e.g. App
 * Startup). Since this module ships install-time (fused into the base APK, not a true
 * on-demand split — see the module's own manifest), its manifest merges into the
 * app's like any other component, so this runs unconditionally.
 */
@UnstableApi
class CastAutoInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.applicationContext?.let { coordinator = CastSessionCoordinator(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    private companion object {
        // Held statically so the coordinator (and the MediaController/SirCastPlayer it
        // owns) has a GC root for the life of the process, same as this provider does.
        // Only ever constructed with applicationContext (see onCreate above), so this
        // is not the Activity/Fragment leak lint's StaticFieldLeak check guards
        // against — it can't tell the difference between an application and any other
        // Context, though.
        @SuppressLint("StaticFieldLeak")
        private var coordinator: CastSessionCoordinator? = null
    }
}
