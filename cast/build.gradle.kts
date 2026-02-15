plugins {
    alias(libs.plugins.android.dynamic.feature)
}

android {
    namespace = "com.cascadiacollections.sir.cast"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Note: Kotlin toolchain inherited from :app base module (JDK 17 Adoptium Temurin)

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

