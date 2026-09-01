plugins {
    id("proportion.android.library")
    id("proportion.android.library.compose")
}

android {
    namespace = "com.ilsecondodasinistra.proportion.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
}
