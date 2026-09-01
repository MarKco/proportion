plugins {
    id("proportion.android.library")
    id("proportion.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ilsecondodasinistra.proportion.core.data"
}

dependencies {
    api(projects.core.domain)
    api(projects.core.model)
    api(projects.core.transfer)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
