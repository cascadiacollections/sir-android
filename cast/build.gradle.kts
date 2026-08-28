plugins {
    id("sir.android.feature")
}

android {
    namespace = "com.cascadiacollections.sir.cast"

    defaultConfig {
        missingDimensionStrategy("distribution", "play")
    }
}

dependencies {
    // Base app module - provides Media3 common types
    implementation(project(":app"))

    // Cast SDK - use version catalog for consistent versioning
    implementation(libs.media3.cast)
    implementation(libs.media3.common)
    // MediaController/SessionToken, to connect to RadioPlaybackService's session from
    // this module the same way RadioViewModel does from :app.
    implementation(libs.media3.session)
    implementation(libs.play.services.cast.framework)
    implementation(libs.mediarouter)

    // Coroutines
    implementation(libs.kotlinx.coroutines.guava)
}
