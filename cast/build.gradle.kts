plugins {
    id("sir.android.feature")
}

android {
    namespace = "com.cascadiacollections.sir.cast"

    // Mirrors :app's flavors so each base variant gets its own build of this
    // module. Previously this module always built against :app's play variant,
    // which provides the Cast SDK's transitive deps (play-services-basement,
    // mediarouter), so they were stripped from the feature — and the foss APK
    // shipped Cast code without them and crashed at startup in
    // CastAutoInitializer. All Cast code and dependencies are play-only now.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }
}

dependencies {
    // Base app module - provides Media3 common types
    implementation(project(":app"))

    // Cast SDK - use version catalog for consistent versioning
    "playImplementation"(libs.media3.cast)
    "playImplementation"(libs.media3.common)
    // MediaController/SessionToken, to connect to RadioPlaybackService's session from
    // this module the same way RadioViewModel does from :app.
    "playImplementation"(libs.media3.session)
    "playImplementation"(libs.play.services.cast.framework)
    "playImplementation"(libs.mediarouter)

    // Coroutines
    "playImplementation"(libs.kotlinx.coroutines.guava)
}
