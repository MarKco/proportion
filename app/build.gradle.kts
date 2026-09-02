import java.util.Properties

plugins {
    id("proportion.android.application")
    id("proportion.android.library.compose")
    id("proportion.hilt")
    alias(libs.plugins.kotlin.serialization)
}

// Release signing reads from local.properties (gitignored, never committed):
//   RELEASE_STORE_FILE=/absolute/path/to/your.jks
//   RELEASE_STORE_PASSWORD=...
//   RELEASE_KEY_ALIAS=...
//   RELEASE_KEY_PASSWORD=...
// Missing local.properties, or missing any of the four keys above, leaves the release
// build unsigned, exactly as before this wiring was added. (local.properties also holds
// AGP's own sdk.dir key, untouched by this block.)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseSigningKeys = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD",
)
val hasReleaseSigningConfig = releaseSigningKeys.all { localProperties.getProperty(it) != null }

android {
    namespace = "com.ilsecondodasinistra.proportion"

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    if (hasReleaseSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = file(localProperties.getProperty("RELEASE_STORE_FILE"))
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.feature.home)
    implementation(projects.feature.recipes)
    implementation(projects.feature.editor)
    implementation(projects.feature.cook)
    implementation(projects.feature.shopping)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    // Only for AndroidManifest.xml's AppLocalesMetadataHolderService reference — the app module
    // never calls AppCompat directly, that lives behind LocaleController in :core:ui.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.androidx.test.ext.junit)

    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
