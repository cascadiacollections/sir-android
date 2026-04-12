plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "sir.android.app"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidDynamicFeature") {
            id = "sir.android.feature"
            implementationClass = "AndroidDynamicFeatureConventionPlugin"
        }
        register("androidLibrary") {
            id = "sir.android.lib"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidWear") {
            id = "sir.android.wear"
            implementationClass = "AndroidWearConventionPlugin"
        }
    }
}
