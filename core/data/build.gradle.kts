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
    implementation(projects.core.sync)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.androidx.work.testing)
}
