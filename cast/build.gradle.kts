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

    // Cast SDK - needs explicit media3-cast dependency
    implementation("androidx.media3:media3-cast:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation(libs.mediarouter)

    // Coroutines
    implementation(libs.kotlinx.coroutines.guava)
}

