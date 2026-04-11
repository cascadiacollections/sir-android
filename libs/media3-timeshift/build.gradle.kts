plugins {
    id("sir.android.lib")
}

android {
    namespace = "com.cascadiacollections.android.media3.timeshift"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(libs.media3.datasource)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
