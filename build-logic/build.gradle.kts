plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
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
    }
}
