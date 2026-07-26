plugins {
    id("sir.android.lib")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.cascadiacollections.sir.core.model"
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
