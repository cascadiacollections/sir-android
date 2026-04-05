import java.util.Properties

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:14.0.0-b03")
    }
}

plugins {
    id("sir.android.app")
    alias(libs.plugins.kotlin.compose)
    id("jacoco")
}

apply(plugin = "com.mikepenz.aboutlibraries.plugin")

// Apply Firebase plugins only when google-services.json is available (Play builds)
if (file("src/play/google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// Load keystore.properties for local development (CI uses env vars instead)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

// Load local.properties for machine-specific overrides (gitignored)
val localProperties = Properties()
rootProject.file("local.properties").takeIf { it.exists() }
    ?.inputStream()?.use { localProperties.load(it) }

android {
    namespace = "com.cascadiacollections.sir"

    signingConfigs {
        create("release") {
            val alias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
                ?: keystoreProperties["keyAlias"]?.toString()
            val keyPwd = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: keystoreProperties["keyPassword"]?.toString()
            val storePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { file(it) }
                ?: keystoreProperties["storeFile"]?.toString()?.let { rootProject.file(it) }
            val storePwd = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: keystoreProperties["storePassword"]?.toString()
            if (alias != null && keyPwd != null && storePath != null && storePwd != null) {
                keyAlias = alias
                keyPassword = keyPwd
                storeFile = storePath
                storePassword = storePwd
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("foss") {
            dimension = "distribution"
        }
    }

    defaultConfig {
        applicationId = "com.cascadiacollections.sir"
        versionCode = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
            .standardOutput.asText.get().trim().toIntOrNull() ?: 1
        versionName = "1.2.0"

        // Include ARM ABIs for phones/tablets + x86_64 for ChromeOS
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile != null) signingConfig = releaseSigningConfig
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = true
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

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

// Disable google-services processing for FOSS builds (no Firebase)
tasks.configureEach {
    if (name.contains("Foss") && name.contains("GoogleServices")) {
        enabled = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // Media3 with OkHttp for optimized HTTP streaming (no UI module - audio only)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // OkHttp with BOM for consistent versioning
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    // Logging interceptor - guarded by BuildConfig.DEBUG at runtime, stripped by R8 in release
    implementation(libs.okhttp.logging.interceptor)

    // Cast device detection (lightweight - actual Cast player in dynamic module)
    implementation(libs.mediarouter)

    // Dynamic feature delivery for on-demand Cast module
    implementation(libs.play.feature.delivery.ktx)

    // Settings persistence
    implementation(libs.datastore.preferences)

    // Splash screen
    implementation(libs.androidx.splashscreen)

    // OSS licenses UI
    implementation(libs.aboutlibraries.compose)

    // Glance widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Baseline Profiles - enables AOT compilation for faster startup
    implementation(libs.androidx.profileinstaller)

    implementation(libs.kotlinx.coroutines.guava)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Firebase (Play builds only)
    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.analytics)
    "playImplementation"(libs.firebase.crashlytics)

    // Memory leak detection in debug builds (runs in separate process to avoid dynamic feature conflicts)
    debugImplementation(libs.leakcanary.android)
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testPlayDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val kotlinClasses = fileTree("${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/playDebug/compilePlayDebugKotlin/classes") {
        exclude(
            "**/R.class", "**/R$*.class",
            "**/BuildConfig.class",
            "**/ui/theme/**",
            "**/*Preview*.class",
        )
    }

    classDirectories.setFrom(kotlinClasses)
    sourceDirectories.setFrom("${projectDir}/src/main/java")
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/testPlayDebugUnitTest.exec") }
    )
}
