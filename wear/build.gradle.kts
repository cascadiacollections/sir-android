plugins {
    id("sir.android.wear")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cascadiacollections.sir.wear"

    defaultConfig {
        // The phone app's applicationId: the Wearable Data Layer only delivers between apps
        // with the same package name and signing key, so the phone→watch station sync
        // (StationSyncListenerService) needs the two to match. The namespace (R class,
        // Kotlin package) stays .wear.
        applicationId = "com.cascadiacollections.sir"
        missingDimensionStrategy("distribution", "play")
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)

    // Wear Compose Material 3
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)

    // Wear Tile (RadioTileService): androidx.wear.tiles.TileService base class, with the
    // layout built via androidx.wear.protolayout builders (the newer, non-deprecated API).
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.tiles.material)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)
    debugImplementation(libs.androidx.wear.tiles.tooling.preview)
    debugImplementation(libs.leakcanary.android)

    // Media3 for standalone streaming
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    // Directory stations flagged HLS (`.m3u8`) chosen from the synced recents
    implementation(libs.media3.exoplayer.hls)

    // Phone → watch station sync (Wearable Data Layer) and the "Play last" complication
    implementation(projects.core.model)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.wear.watchface.complications.data.source.ktx)

    // Shared playback policy (stream URL, retry backoff) and OkHttp client factory
    implementation(projects.core.playback)
    implementation(project(":libs:okhttp-streaming"))
    implementation(project(":libs:notification-colors"))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.guava)

    testImplementation(libs.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
