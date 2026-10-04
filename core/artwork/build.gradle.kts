plugins {
    id("sir.android.lib")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.cascadiacollections.sir.core.artwork"
}

dependencies {
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.coroutines.test)
}
