plugins {
    id("sir.android.lib")
}

android {
    namespace = "com.cascadiacollections.sir.notificationcolors"
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
