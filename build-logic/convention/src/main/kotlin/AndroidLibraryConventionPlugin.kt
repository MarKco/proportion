import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 ships built-in Kotlin support; applying org.jetbrains.kotlin.android is an error.
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = ProportionVersions.COMPILE_SDK

            defaultConfig {
                minSdk = ProportionVersions.MIN_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            testOptions {
                unitTests.isIncludeAndroidResources = true
            }
        }

        tasks.withType<Test>().configureEach {
            // Modules whose screens are still placeholders have no tests yet, and Gradle 9 treats
            // an empty test task as a failure.
            failOnNoDiscoveredTests.set(false)
        }

        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("truth").get())
        }
    }
}
