import com.android.build.gradle.DynamicFeatureExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidDynamicFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.dynamic-feature")
            pluginManager.apply("org.jetbrains.kotlin.android")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            configure<DynamicFeatureExtension> {
                compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()

                defaultConfig {
                    minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            configure<KotlinAndroidProjectExtension> {
                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                    vendor.set(JvmVendorSpec.ADOPTIUM)
                }
            }
        }
    }
}
