plugins {
    id("sir.android.lib")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.cascadiacollections.sir.core.directory"
}

dependencies {
    api(projects.core.model)
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
