plugins {
    id("proportion.android.library")
    id("proportion.android.library.compose")
    id("proportion.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ilsecondodasinistra.proportion.feature.editor"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)

    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
