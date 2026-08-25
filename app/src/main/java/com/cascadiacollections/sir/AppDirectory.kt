package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.directory.RadioDirectories
import com.cascadiacollections.sir.core.directory.RadioDirectory

/**
 * Process-wide [RadioDirectory].
 *
 * The chain owns an [okhttp3.OkHttpClient] (connection pool plus dispatcher threads) and
 * an in-memory response cache, so it must outlive any single Activity. Building it per
 * composition would both leak thread pools across configuration changes and throw the
 * cache away on exactly the rotation and back-navigation cases it exists to serve.
 *
 * Holds no [android.content.Context], so it is safe as a static singleton.
 */
object AppDirectory {
    val instance: RadioDirectory by lazy { RadioDirectories.create() }
}
