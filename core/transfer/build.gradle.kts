plugins {
    id("proportion.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    implementation(libs.kotlinx.serialization.json)
}
