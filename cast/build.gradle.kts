plugins {
    id("sir.android.feature")
}

android {
    namespace = "com.cascadiacollections.sir.cast"
}

dependencies {
    // Base app module - provides Media3 common types
    implementation(project(":app"))

    // Cast SDK - use version catalog for consistent versioning
    implementation(libs.media3.cast)
    implementation(libs.media3.common)
    implementation(libs.play.services.cast.framework)
    implementation(libs.mediarouter)

    // Coroutines
    implementation(libs.kotlinx.coroutines.guava)
}
