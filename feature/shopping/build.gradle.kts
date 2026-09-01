plugins {
    id("proportion.android.library")
    id("proportion.android.library.compose")
    id("proportion.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ilsecondodasinistra.proportion.feature.shopping"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
}
