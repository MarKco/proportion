import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 ships built-in Kotlin support; applying org.jetbrains.kotlin.android is an error.
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = ProportionVersions.COMPILE_SDK

            defaultConfig {
                applicationId = "com.ilsecondodasinistra.proportion"
                minSdk = ProportionVersions.MIN_SDK
                targetSdk = ProportionVersions.TARGET_SDK
                versionCode = 2
                versionName = "2.0"
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            // Robolectric needs the merged manifest and resources of the variant under test.
            testOptions {
                unitTests.isIncludeAndroidResources = true
            }
        }

        tasks.withType<Test>().configureEach {
            // Modules whose screens are still placeholders have no tests yet, and Gradle 9 treats
            // an empty test task as a failure.
            failOnNoDiscoveredTests.set(false)
        }
    }
}
