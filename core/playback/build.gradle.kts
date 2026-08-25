plugins {
    id("sir.android.lib")
}

android {
    namespace = "com.cascadiacollections.sir.core.playback"
}

dependencies {
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
}
