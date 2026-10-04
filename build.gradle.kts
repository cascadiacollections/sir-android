// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.aboutlibraries.plugin) apply false
    alias(libs.plugins.detekt) apply false
}

// Configure Java toolchain for all subprojects
subprojects {
    plugins.withType<JavaBasePlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
                // Use Eclipse Adoptium Temurin - OSS JDK with enterprise-grade support
                vendor.set(JvmVendorSpec.ADOPTIUM)
            }
        }
    }
}

// Static analysis for every module: detekt (+ ktlint formatting and Compose rules).
// Findings that predate a rule live in each module's detekt-baseline.xml; new code must be clean.
// `./gradlew detekt` checks, `--auto-correct` applies formatting fixes, `detektBaseline` regenerates baselines.
val detektRulePlugins = listOf(libs.detekt.rules.ktlint.wrapper, libs.compose.rules.detekt)
val detektReportMerge by tasks.registering(dev.detekt.gradle.report.ReportMergeTask::class) {
    output.set(layout.buildDirectory.file("reports/detekt/detekt.sarif"))
}
subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline.set(file("detekt-baseline.xml"))
        basePath.set(rootProject.layout.projectDirectory)
        // Every source set, including product flavors (src/play, src/foss) and tests.
        source.setFrom("src", "build.gradle.kts")
    }
    dependencies {
        detektRulePlugins.forEach { "detektPlugins"(it) }
    }
    // One SARIF file for GitHub code scanning (see .github/workflows/build.yml).
    val detekt = tasks.named<dev.detekt.gradle.Detekt>("detekt") { finalizedBy(detektReportMerge) }
    detektReportMerge.configure { input.from(detekt.flatMap { it.reports.sarif.outputLocation }) }
}
