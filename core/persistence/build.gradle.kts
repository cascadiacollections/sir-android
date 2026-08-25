plugins {
    id("sir.android.lib")
}

android {
    namespace = "com.cascadiacollections.sir.core.persistence"
}

dependencies {
    api(projects.core.model)
    api(projects.core.playback)
    api(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
