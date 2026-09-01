plugins {
    id("proportion.android.library")
    id("proportion.hilt")
}

android {
    namespace = "com.ilsecondodasinistra.proportion.core.datastore"
}

dependencies {
    api(projects.core.model)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
