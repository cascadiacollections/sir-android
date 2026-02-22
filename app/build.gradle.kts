plugins {
    id("sir.android.app")
}

android {
    namespace = "com.cascadiacollections.sir"

    defaultConfig {
        applicationId = "com.cascadiacollections.sir"
        versionCode = 1
        versionName = "1.0"

        // Include ARM ABIs for phones/tablets + x86_64 for ChromeOS
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // Benchmark build type for baseline profile generation
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            // Disable obfuscation for profiling
            proguardFiles("benchmark-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Dynamic feature modules
    dynamicFeatures += setOf(":cast")

    // Optimize packaging
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/*.version"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/kotlin/**"
            excludes += "/DebugProbesKt.bin"
            excludes += "/*.txt"
            excludes += "/*.properties"
        }
        // Use native libraries compression for smaller APK, faster load on Android 6+
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // Enable experimental AGP optimizations
    dependenciesInfo {
        // Disable dependency metadata in APK (saves ~2KB)
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        // Enable aggressive inlining for better performance
        freeCompilerArgs.addAll(
            "-Xno-call-assertions",      // Skip null checks on platform types (we trust Android APIs)
            "-Xno-param-assertions",     // Skip parameter null checks
            "-Xno-receiver-assertions",  // Skip receiver null checks
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Media3 with OkHttp for optimized HTTP streaming (no UI module - audio only)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // OkHttp with BOM for consistent versioning
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    // Cast device detection (lightweight - actual Cast player in dynamic module)
    implementation(libs.mediarouter)

    // Dynamic feature delivery for on-demand Cast module
    implementation(libs.play.feature.delivery.ktx)

    // Settings persistence
    implementation(libs.datastore.preferences)

    // Baseline Profiles - enables AOT compilation for faster startup
    implementation(libs.androidx.profileinstaller)

    implementation(libs.kotlinx.coroutines.guava)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Note: LeakCanary removed due to incompatibility with dynamic feature modules
    // See: https://github.com/square/leakcanary/issues/2169
}
