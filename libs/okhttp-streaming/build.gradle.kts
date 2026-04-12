plugins {
    id("sir.android.lib")
}

android {
    namespace = "com.cascadiacollections.sir.okhttp.streaming"
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
}
