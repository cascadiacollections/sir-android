import com.android.build.api.dsl.Lint
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog

/**
 * Android Lint settings shared by every module: warnings fail the build, findings that
 * predate a check live in the module's lint-baseline.xml, and Slack's Compose checks
 * run alongside the built-in ones. SARIF output feeds GitHub code scanning.
 */
internal fun Project.configureLint(lint: Lint, libs: VersionCatalog) {
    lint.apply {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        sarifReport = true
        // Version-bump nags are Renovate's/Dependabot's job, not a build failure.
        disable += setOf("OldTargetApi", "NewerVersionAvailable", "ObsoleteSdkInt", "GradleDependency", "AndroidGradlePluginVersion")
        // Off-by-default checks worth having.
        enable += setOf("StopShip", "WrongThreadInterprocedural", "UnusedIds", "ComposeM2Api")
    }
    dependencies.add("lintChecks", libs.findLibrary("compose-lint-checks").get())
}
