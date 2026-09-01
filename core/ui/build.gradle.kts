plugins {
    id("proportion.android.library")
    id("proportion.android.library.compose")
    id("proportion.hilt")
}

android {
    namespace = "com.ilsecondodasinistra.proportion.core.ui"
}

dependencies {
    api(projects.core.domain)
    api(projects.core.designsystem)

    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
