plugins {
    id("sir.android.lib")
}

android {
    namespace = "com.cascadiacollections.sir.core.persistence"
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
