plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
    }
}

/**
 * Every unit test in the build, in one task.
 *
 * The JVM modules expose `test` and the Android ones `testDebugUnitTest`, so the full check used to
 * be a hand-written list of both — and a module added later would silently never run. This asks each
 * subproject for whichever of the two it actually has.
 */
tasks.register("testAll") {
    group = "verification"
    description = "Runs the unit tests of every module, JVM and Android alike."

    dependsOn(
        provider {
            subprojects.flatMap { module ->
                module.tasks.matching { it.name == "test" || it.name == "testDebugUnitTest" }
            }
        },
    )
}

/**
 * What CI runs and what a phase has to pass before it is called done: static analysis, lint, every
 * test, and an APK that actually builds.
 */
tasks.register("verifyAll") {
    group = "verification"
    description = "detekt + lint + every unit test + a debug APK."

    dependsOn(
        provider {
            subprojects.flatMap { module -> module.tasks.matching { it.name == "detekt" || it.name == "lint" } }
        },
    )
    dependsOn("testAll", ":app:assembleDebug")
}
