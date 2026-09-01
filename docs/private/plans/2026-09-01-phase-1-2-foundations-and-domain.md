# ProPortion Phases 1–2 — Foundations and Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the multi-module Android project with its design system and navigation shell, then build and fully test the scaling domain plus the Room persistence layer — with no feature UI yet.

**Architecture:** Now-in-Android style multi-module Gradle build with convention plugins in `build-logic`. `:core:domain` is pure Kotlin (no `android.*`) and holds the scaling engine, unit rules and repository interfaces; `:core:data` implements those interfaces on top of `:core:database` (Room) and `:core:datastore` (DataStore). Features depend on the domain, never on each other.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Navigation Compose, Hilt, Room (KSP), DataStore Preferences, kotlinx.serialization, Coroutines/Flow, JUnit4 + kotlin.test + Truth, Robolectric-free JVM tests for the domain, detekt, GitHub Actions.

**Spec:** `docs/private/specs/2026-09-01-proportion-v1-design.md`

## Global Constraints

- Package root: `com.ilsecondodasinistra.proportion`. App name: ProPortion. Author: Marco Zanetti.
- **Never run `git commit` or `git push`.** Marco commits himself. Where a task is complete, report it and update `docs/private/IMPLEMENTATION-STATUS.md` instead of committing.
- **Never reference Marco's employer** anywhere in code, docs, or metadata.
- minSdk 26, targetSdk 36, compileSdk 36, JVM target 17.
- No hardcoded user-facing strings anywhere — every one goes in `strings.xml`. Default `values/` is English; `values-it/` is Italian.
- `:core:domain` must not import `android.*` or `androidx.*`. This is an architectural invariant and is asserted by a test.
- Features never depend on other features.
- All entity primary keys are UUID strings.
- `Ingredient.density_g_per_ml` exists in schema v1 but is unused — it is v2 preparation, do not delete it.
- `UnitConverter.convert` takes an `ingredient` parameter in v1 even though v1 ignores it.
- Every domain behaviour is written test-first.

---

## File structure

```
build-logic/convention/                        convention plugins, applied by every module
gradle/libs.versions.toml                      single source of dependency versions
settings.gradle.kts                            module registry
app/                                           MainActivity, NavHost, Hilt application
core/model/                                    Recipe, Ingredient, MeasureUnit, Tag … (pure data)
core/domain/                                   UnitConverter, QuantityFormatter, RecipeScaler,
                                               ScaleConstraint/Result/Warning, repository interfaces
core/database/                                 Room entities, DAOs, converters, migrations, seeding
core/datastore/                                UserPreferences on DataStore
core/data/                                     repository implementations, mappers
core/designsystem/                             colour, type, shapes, theme, motion
core/ui/                                       shared composables that know domain models
feature/…                                      empty placeholder screens in this plan
docs/private/IMPLEMENTATION-STATUS.md          living checklist, updated at the end of each task
```

Rationale for the domain split: `UnitConverter` and `QuantityFormatter` are independently testable
and used by both the scaler and the shopping-list merge, so they are their own files rather than
private helpers of the scaler.

---

## Task 1: Gradle skeleton that builds

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`, `AndroidLibraryConventionPlugin.kt`, `AndroidComposeConventionPlugin.kt`, `JvmLibraryConventionPlugin.kt`, `HiltConventionPlugin.kt`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/ilsecondodasinistra/proportion/ProPortionApplication.kt`, `MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-it/strings.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: convention plugin ids `proportion.android.application`, `proportion.android.library`, `proportion.android.library.compose`, `proportion.jvm.library`, `proportion.hilt` — every later module applies these instead of repeating configuration.

- [ ] **Step 1: Create the version catalog**

Create `gradle/libs.versions.toml`. **These versions are verified against the live repositories and
against a real build (2026-09-01)** — do not "modernise" them without re-running the build:
AGP 9.4.0 requires Gradle 9.7.1, Hilt 2.60.1 requires AGP 9+, and AndroidX is capped by the fact that
`platforms;android-37` is not yet in the stable SDK channel.

```toml
[versions]
agp = "9.4.0"
kotlin = "2.3.21"
ksp = "2.3.11"
hilt = "2.60.1"
coreKtx = "1.18.0"
lifecycle = "2.10.0"
activityCompose = "1.12.4"
composeBom = "2026.05.01"
navigation = "2.9.8"
room = "2.8.4"
datastore = "1.2.1"
serialization = "1.11.0"
coroutines = "1.11.0"
junit = "4.13.2"
truth = "1.4.5"
turbine = "1.2.1"
androidxTestExt = "1.3.0"
detekt = "1.23.8"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidxCore" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "androidxActivity" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "androidxLifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "androidxLifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExt" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }

# used by build-logic to apply plugins programmatically
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

- [ ] **Step 2: Create settings and the build-logic module**

`settings.gradle.kts` — the module list grows in later tasks; register only what exists now:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ProPortion"
include(":app")
```

`build-logic/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "build-logic"
include(":convention")
```

`build-logic/convention/build.gradle.kts`:

```kotlin
plugins { `kotlin-dsl` }
group = "com.ilsecondodasinistra.proportion.buildlogic"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "proportion.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "proportion.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "proportion.android.library.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "proportion.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("hilt") {
            id = "proportion.hilt"
            implementationClass = "HiltConventionPlugin"
        }
    }
}
```

- [ ] **Step 3: Write the convention plugins**

**AGP 9 note:** AGP 9 has built-in Kotlin support, so a convention plugin must **not** apply
`org.jetbrains.kotlin.android` — doing so fails the build. Kotlin's JVM target follows
`compileOptions`, so the Android convention plugins only configure the Android extension.

`AndroidLibraryConventionPlugin.kt`:

```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = 36
            defaultConfig {
                minSdk = 26
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }
}
```

`AndroidApplicationConventionPlugin.kt` is the same shape but applies `com.android.application`,
configures `ApplicationExtension`, and sets `defaultConfig { applicationId =
"com.ilsecondodasinistra.proportion"; targetSdk = 36; versionCode = 1; versionName = "1.0" }`.

`AndroidComposeConventionPlugin.kt`:

```kotlin
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<CommonExtension<*, *, *, *, *, *>> {
            buildFeatures { compose = true }
        }
        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
```

`JvmLibraryConventionPlugin.kt` applies `org.jetbrains.kotlin.jvm` and sets the Java 17 toolchain.
`HiltConventionPlugin.kt` applies `com.google.devtools.ksp` and `com.google.dagger.hilt.android`,
then adds `implementation(libs.hilt.android)` and `ksp(libs.hilt.compiler)`.

- [ ] **Step 4: Create the `:app` module with a placeholder screen**

`app/build.gradle.kts`:

```kotlin
plugins {
    id("proportion.android.application")
    id("proportion.android.library.compose")
    id("proportion.hilt")
}

android {
    namespace = "com.ilsecondodasinistra.proportion"
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
```

`ProPortionApplication.kt`:

```kotlin
package com.ilsecondodasinistra.proportion

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ProPortionApplication : Application()
```

`MainActivity.kt` sets a Compose content root showing `stringResource(R.string.app_name)` centred —
it is replaced in Task 12. `AndroidManifest.xml` declares the application class, the launcher
activity, and `android:localeConfig`. `values/strings.xml` holds `app_name` = "ProPortion";
`values-it/strings.xml` mirrors it.

- [ ] **Step 5: Verify the build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If any dependency version in the catalog fails to resolve, bump it to
the latest stable release, re-run, and note the change in the catalog.

- [ ] **Step 6: Update the status checklist**

Tick "Gradle multi-module scaffolding + version catalog" in `docs/private/IMPLEMENTATION-STATUS.md`
and set the "Last updated" date. Do not commit.

---

## Task 2: CI and static analysis

**Files:**
- Create: `.github/workflows/ci.yml`, `config/detekt/detekt.yml`
- Modify: root `build.gradle.kts` (apply detekt to all subprojects)

**Interfaces:**
- Consumes: the Gradle build from Task 1.
- Produces: a `check` entry point that later tasks rely on: `./gradlew detekt lint test`.

- [ ] **Step 1: Apply detekt in the root build**

```kotlin
plugins {
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
    }
}
```

- [ ] **Step 2: Generate the detekt baseline config**

Run: `./gradlew detektGenerateConfig` then move the generated file to `config/detekt/detekt.yml`.
Set `MaxLineLength.maxLineLength: 120` and disable `MagicNumber` for `**/test/**`.

- [ ] **Step 3: Write the CI workflow**

`.github/workflows/ci.yml`:

```yaml
name: CI
on:
  push:
    branches: [ main ]
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew detekt lint testDebugUnitTest assembleDebug --stacktrace
```

- [ ] **Step 4: Verify locally**

Run: `./gradlew detekt lint testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any detekt finding rather than suppressing it.

- [ ] **Step 5: Update the status checklist**

Tick "GitHub Actions CI green".

---

## Task 3: `:core:model` and typed units

**Files:**
- Create: `core/model/build.gradle.kts`
- Create: `core/model/src/main/kotlin/com/ilsecondodasinistra/proportion/core/model/MeasureUnit.kt`, `UnitCategory.kt`, `Recipe.kt`, `Ingredient.kt`, `Tag.kt`, `RecipeIngredient.kt`, `ScaleVariant.kt`, `ShoppingItem.kt`
- Test: `core/model/src/test/kotlin/com/ilsecondodasinistra/proportion/core/model/MeasureUnitTest.kt`
- Modify: `settings.gradle.kts` (add `include(":core:model")`)

**Interfaces:**
- Consumes: `proportion.jvm.library` convention plugin.
- Produces: `MeasureUnit` (enum with `category: UnitCategory` and `baseFactor: Double`),
  `UnitCategory` (`MASS`, `VOLUME`, `COUNT`, `APPROXIMATE`), and the data classes
  `Recipe`, `RecipeIngredient`, `Ingredient`, `Tag`, `ScaleVariant`, `ShoppingItem`.

- [ ] **Step 1: Write the failing test**

`MeasureUnitTest.kt`:

```kotlin
package com.ilsecondodasinistra.proportion.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeasureUnitTest {

    @Test
    fun `mass units are expressed in grams`() {
        assertThat(MeasureUnit.GRAM.baseFactor).isEqualTo(1.0)
        assertThat(MeasureUnit.KILOGRAM.baseFactor).isEqualTo(1000.0)
        assertThat(MeasureUnit.KILOGRAM.category).isEqualTo(UnitCategory.MASS)
    }

    @Test
    fun `domestic units are volume units so cups convert to millilitres`() {
        assertThat(MeasureUnit.CUP.category).isEqualTo(UnitCategory.VOLUME)
        assertThat(MeasureUnit.CUP.baseFactor).isEqualTo(240.0)
        assertThat(MeasureUnit.TABLESPOON.baseFactor).isEqualTo(15.0)
        assertThat(MeasureUnit.TEASPOON.baseFactor).isEqualTo(5.0)
        assertThat(MeasureUnit.GLASS.baseFactor).isEqualTo(200.0)
    }

    @Test
    fun `count units are discrete`() {
        assertThat(MeasureUnit.EGG.category).isEqualTo(UnitCategory.COUNT)
        assertThat(MeasureUnit.EGG.isDiscrete).isTrue()
        assertThat(MeasureUnit.GRAM.isDiscrete).isFalse()
    }

    @Test
    fun `approximate units are never scalable`() {
        assertThat(MeasureUnit.TO_TASTE.isScalable).isFalse()
        assertThat(MeasureUnit.PINCH.isScalable).isFalse()
        assertThat(MeasureUnit.GRAM.isScalable).isTrue()
    }

    @Test
    fun `every unit belongs to a category and every category has a base unit`() {
        MeasureUnit.entries.forEach { unit ->
            assertThat(unit.category).isNotNull()
        }
        assertThat(MeasureUnit.entries.filter { it.baseFactor == 1.0 && it.category == UnitCategory.MASS })
            .containsExactly(MeasureUnit.GRAM)
        assertThat(MeasureUnit.entries.filter { it.baseFactor == 1.0 && it.category == UnitCategory.VOLUME })
            .containsExactly(MeasureUnit.MILLILITRE)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:model:test`
Expected: FAIL — unresolved reference `MeasureUnit`.

- [ ] **Step 3: Create the module and the model**

`core/model/build.gradle.kts`:

```kotlin
plugins {
    id("proportion.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
```

`UnitCategory.kt` and `MeasureUnit.kt`:

```kotlin
package com.ilsecondodasinistra.proportion.core.model

enum class UnitCategory { MASS, VOLUME, COUNT, APPROXIMATE }

/**
 * @param baseFactor how many base units one of this unit is worth.
 * Base units: gram for MASS, millilitre for VOLUME, one piece for COUNT.
 */
enum class MeasureUnit(val category: UnitCategory, val baseFactor: Double) {
    GRAM(UnitCategory.MASS, 1.0),
    KILOGRAM(UnitCategory.MASS, 1000.0),

    MILLILITRE(UnitCategory.VOLUME, 1.0),
    LITRE(UnitCategory.VOLUME, 1000.0),
    TEASPOON(UnitCategory.VOLUME, 5.0),
    TABLESPOON(UnitCategory.VOLUME, 15.0),
    GLASS(UnitCategory.VOLUME, 200.0),
    CUP(UnitCategory.VOLUME, 240.0),

    PIECE(UnitCategory.COUNT, 1.0),
    EGG(UnitCategory.COUNT, 1.0),
    CLOVE(UnitCategory.COUNT, 1.0),
    SLICE(UnitCategory.COUNT, 1.0),
    LEAF(UnitCategory.COUNT, 1.0),
    SACHET(UnitCategory.COUNT, 1.0),
    JAR(UnitCategory.COUNT, 1.0),

    TO_TASTE(UnitCategory.APPROXIMATE, 0.0),
    PINCH(UnitCategory.APPROXIMATE, 0.0),
    DRIZZLE(UnitCategory.APPROXIMATE, 0.0);

    val isDiscrete: Boolean get() = category == UnitCategory.COUNT
    val isScalable: Boolean get() = category != UnitCategory.APPROXIMATE
}
```

Remaining model files, all plain immutable data classes with UUID string ids:

```kotlin
data class Recipe(
    val id: String,
    val title: String,
    val servings: Int?,
    val steps: List<String>,
    val ingredients: List<RecipeIngredient>,
    val tags: List<Tag>,
    val notes: String? = null,
    val isFavourite: Boolean = false,
    val cookCount: Int = 0,
    val lastCookedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class RecipeIngredient(
    val id: String,
    val ingredient: Ingredient,
    val position: Int,
    val quantity: Double?,
    val unit: MeasureUnit,
    val displayText: String? = null,
    val note: String? = null,
)

data class Ingredient(
    val id: String,
    val name: String,
    val normalisedName: String,
    val defaultUnit: MeasureUnit = MeasureUnit.GRAM,
    /** v2 preparation: unused in v1, see spec §12. Never remove. */
    val densityGramsPerMl: Double? = null,
)

data class Tag(
    val id: String,
    val key: String?,        // set for built-ins, resolved through strings.xml
    val name: String?,       // set for user tags, never translated
    val isBuiltIn: Boolean,
    val colorIndex: Int = 0,
) {
    init { require((key == null) != (name == null)) { "exactly one of key/name must be set" } }
}

data class ScaleVariant(
    val id: String,
    val recipeId: String,
    val label: String,
    val constraintPayload: String,   // serialised ScaleConstraint
    val isDefault: Boolean = false,
    val createdAt: Long = 0L,
)

data class ShoppingItem(
    val id: String,
    val ingredient: Ingredient,
    val quantity: Double?,
    val unit: MeasureUnit,
    val isChecked: Boolean = false,
    val sourceRecipeIds: List<String> = emptyList(),
)
```

Add `include(":core:model")` to `settings.gradle.kts`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:model:test`
Expected: PASS, 5 tests.

- [ ] **Step 5: Update the status checklist**

Tick "Model and typed units".

---

## Task 4: `UnitConverter` — conversion inside a category

**Files:**
- Create: `core/domain/build.gradle.kts`
- Create: `core/domain/src/main/kotlin/…/core/domain/unit/UnitConverter.kt`, `DefaultUnitConverter.kt`, `IngredientRef.kt`, `DensityRepository.kt`
- Test: `core/domain/src/test/kotlin/…/core/domain/unit/DefaultUnitConverterTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `MeasureUnit`, `UnitCategory` from Task 3.
- Produces: `UnitConverter.convert(qty, from, to, ingredient): Double?` returning `null` when the
  conversion is impossible; `DensityRepository`; `NoDensityRepository`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.unit

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

class DefaultUnitConverterTest {

    private val converter = DefaultUnitConverter()

    @Test
    fun `converts within the mass category`() {
        assertThat(converter.convert(1500.0, MeasureUnit.GRAM, MeasureUnit.KILOGRAM)).isEqualTo(1.5)
        assertThat(converter.convert(0.25, MeasureUnit.KILOGRAM, MeasureUnit.GRAM)).isEqualTo(250.0)
    }

    @Test
    fun `converts cups to millilitres because both are volume`() {
        assertThat(converter.convert(2.0, MeasureUnit.CUP, MeasureUnit.MILLILITRE)).isEqualTo(480.0)
        assertThat(converter.convert(45.0, MeasureUnit.MILLILITRE, MeasureUnit.TABLESPOON)).isEqualTo(3.0)
    }

    @Test
    fun `refuses mass to volume in v1 because density is unknown`() {
        assertThat(converter.convert(100.0, MeasureUnit.GRAM, MeasureUnit.MILLILITRE)).isNull()
    }

    @Test
    fun `refuses conversion involving an approximate unit`() {
        assertThat(converter.convert(1.0, MeasureUnit.PINCH, MeasureUnit.GRAM)).isNull()
        assertThat(converter.convert(1.0, MeasureUnit.GRAM, MeasureUnit.TO_TASTE)).isNull()
    }

    @Test
    fun `count units convert only to themselves`() {
        assertThat(converter.convert(3.0, MeasureUnit.EGG, MeasureUnit.EGG)).isEqualTo(3.0)
        assertThat(converter.convert(3.0, MeasureUnit.EGG, MeasureUnit.SLICE)).isNull()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:domain:test`
Expected: FAIL — unresolved reference `DefaultUnitConverter`.

- [ ] **Step 3: Create the module and the implementation**

`core/domain/build.gradle.kts`:

```kotlin
plugins {
    id("proportion.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.unit

import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.UnitCategory

/** Lightweight reference to an ingredient, so the converter never depends on persistence. */
data class IngredientRef(val id: String, val normalisedName: String)

interface DensityRepository {
    suspend fun densityGramsPerMl(ingredient: IngredientRef): Double?
}

/** v1 binding: no densities are known, so mass <-> volume is always refused. */
class NoDensityRepository : DensityRepository {
    override suspend fun densityGramsPerMl(ingredient: IngredientRef): Double? = null
}

interface UnitConverter {
    /**
     * @param ingredient unused in v1; present so that v2 density conversion needs no signature
     * change (spec §12).
     * @return the converted quantity, or null when the conversion is not possible.
     */
    fun convert(
        qty: Double,
        from: MeasureUnit,
        to: MeasureUnit,
        ingredient: IngredientRef? = null,
    ): Double?
}

class DefaultUnitConverter : UnitConverter {
    override fun convert(qty: Double, from: MeasureUnit, to: MeasureUnit, ingredient: IngredientRef?): Double? {
        if (!from.isScalable || !to.isScalable) return null
        if (from.category != to.category) return null
        if (from.category == UnitCategory.COUNT && from != to) return null
        return qty * from.baseFactor / to.baseFactor
    }
}
```

Add `include(":core:domain")` to `settings.gradle.kts`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:domain:test`
Expected: PASS, 5 tests.

- [ ] **Step 5: Add the architecture invariant test**

`core/domain/src/test/kotlin/…/core/domain/NoAndroidDependencyTest.kt`:

```kotlin
package com.ilsecondodasinistra.proportion.core.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class NoAndroidDependencyTest {

    @Test
    fun `domain sources never import android or androidx`() {
        val offenders = File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file ->
                file.readLines().any { it.startsWith("import android.") || it.startsWith("import androidx.") }
            }
            .map { it.path }
            .toList()
        assertThat(offenders).isEmpty()
    }
}
```

Run: `./gradlew :core:domain:test`
Expected: PASS.

---

## Task 5: `QuantityFormatter` — readable quantities

**Files:**
- Create: `core/domain/src/main/kotlin/…/core/domain/unit/QuantityFormatter.kt`
- Test: `core/domain/src/test/kotlin/…/core/domain/unit/QuantityFormatterTest.kt`

**Interfaces:**
- Consumes: `MeasureUnit`, `UnitCategory`, `UnitConverter`.
- Produces: `QuantityFormatter.format(qty, unit): FormattedQuantity` where
  `FormattedQuantity(value: Double, unit: MeasureUnit, text: String)`. Task 6 onwards uses it to
  fill `ScaledLine.displayText` and `displayUnit`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.unit

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

class QuantityFormatterTest {

    private val formatter = QuantityFormatter(DefaultUnitConverter())

    @Test
    fun `rounds mass to the nearest gram below one hundred grams`() {
        assertThat(formatter.format(37.4, MeasureUnit.GRAM).text).isEqualTo("37 g")
    }

    @Test
    fun `rounds mass to five grams above one hundred grams`() {
        assertThat(formatter.format(453.0, MeasureUnit.GRAM).text).isEqualTo("455 g")
    }

    @Test
    fun `promotes large masses to kilograms`() {
        val formatted = formatter.format(1500.0, MeasureUnit.GRAM)
        assertThat(formatted.unit).isEqualTo(MeasureUnit.KILOGRAM)
        assertThat(formatted.text).isEqualTo("1,5 kg")
    }

    @Test
    fun `renders domestic volumes as human fractions`() {
        assertThat(formatter.format(0.5, MeasureUnit.TABLESPOON).text).isEqualTo("½ cucchiaio")
        assertThat(formatter.format(0.25, MeasureUnit.CUP).text).isEqualTo("¼ tazza")
        assertThat(formatter.format(1.5, MeasureUnit.TEASPOON).text).isEqualTo("1 ½ cucchiaino")
    }

    @Test
    fun `renders discrete quantities without decimals when integral`() {
        assertThat(formatter.format(3.0, MeasureUnit.EGG).text).isEqualTo("3 uova")
    }

    @Test
    fun `keeps the exact value for non integral discrete quantities`() {
        assertThat(formatter.format(1.5, MeasureUnit.EGG).text).isEqualTo("1 ½ uova")
    }

    @Test
    fun `flags quantities below the measurable threshold`() {
        assertThat(formatter.format(0.3, MeasureUnit.GRAM).isBelowThreshold).isTrue()
        assertThat(formatter.format(2.0, MeasureUnit.GRAM).isBelowThreshold).isFalse()
    }
}
```

**Note on unit names:** the formatter produces the numeric part plus a **unit key**, not a
translated word. To keep this test honest, `QuantityFormatter` takes a `UnitNamer` interface
(`fun shortName(unit: MeasureUnit, qty: Double): String`) whose test double returns the Italian
strings above; the Android implementation in `:core:ui` resolves `strings.xml` plurals. Declare
`UnitNamer` in the same file and pass a fake in the test:

```kotlin
private val formatter = QuantityFormatter(DefaultUnitConverter(), FakeItalianUnitNamer())
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:domain:test --tests '*QuantityFormatterTest*'`
Expected: FAIL — unresolved reference `QuantityFormatter`.

- [ ] **Step 3: Implement the formatter**

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.unit

import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.UnitCategory
import kotlin.math.abs
import kotlin.math.round

data class FormattedQuantity(
    val value: Double,
    val unit: MeasureUnit,
    val text: String,
    val isBelowThreshold: Boolean = false,
)

interface UnitNamer {
    fun shortName(unit: MeasureUnit, qty: Double): String
}

class QuantityFormatter(
    private val converter: UnitConverter,
    private val namer: UnitNamer,
) {
    fun format(qty: Double, unit: MeasureUnit): FormattedQuantity { /* see rules below */ }

    companion object {
        const val MEASURABLE_THRESHOLD = 0.5
        val FRACTIONS = mapOf(0.25 to "¼", 0.333 to "⅓", 0.5 to "½", 0.666 to "⅔", 0.75 to "¾")
        const val FRACTION_TOLERANCE = 0.02
    }
}
```

Rules, in order:
1. `APPROXIMATE` → return the unit name alone, value unchanged.
2. `MASS`/`VOLUME` below `MEASURABLE_THRESHOLD` → `isBelowThreshold = true`.
3. `MASS` ≥ 1000 g → promote to kilograms; `VOLUME` ≥ 1000 ml → promote to litres (via `converter`).
4. `MASS`/`VOLUME` in base units → round to 1 below 100, to 5 at or above 100.
5. Domestic volumes and `COUNT` → render the integer part plus the nearest fraction from `FRACTIONS`
   within `FRACTION_TOLERANCE`; fall back to one decimal.
6. Decimal separator is a comma (Italian source language); the Android layer overrides it with a
   locale-aware formatter.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:domain:test --tests '*QuantityFormatterTest*'`
Expected: PASS, 7 tests.

---

## Task 6: `RecipeScaler` — ByServings and ByFactor

**Files:**
- Create: `core/domain/src/main/kotlin/…/core/domain/scale/ScaleConstraint.kt`, `ScaleResult.kt`, `RecipeScaler.kt`, `DefaultRecipeScaler.kt`
- Test: `core/domain/src/test/kotlin/…/core/domain/scale/RecipeScalerServingsTest.kt`
- Test fixture: `core/domain/src/test/kotlin/…/core/domain/scale/TestRecipes.kt`

**Interfaces:**
- Consumes: `QuantityFormatter`, `UnitConverter`, model classes.
- Produces: the full API from spec §5.1 — `ScaleConstraint`, `ScaleResult`, `ScaledRecipe`,
  `ScaledLine`, `ScaleWarning`, `SnapOption`, `ScaleError`, `Leftover`, `AvailableAmount`,
  `RecipeScaler.scale(recipe, constraint)`. Tasks 7–10 extend the same implementation.

- [ ] **Step 1: Write the test fixture**

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.scale

import com.ilsecondodasinistra.proportion.core.model.*

object TestRecipes {

    fun ingredient(name: String, unit: MeasureUnit = MeasureUnit.GRAM) = Ingredient(
        id = "ing-$name", name = name, normalisedName = name.lowercase(), defaultUnit = unit,
    )

    fun line(name: String, qty: Double?, unit: MeasureUnit, position: Int = 0) = RecipeIngredient(
        id = "line-$name", ingredient = ingredient(name, unit), position = position,
        quantity = qty, unit = unit,
    )

    /** Serves 4: 300 g flour, 2 eggs, 120 g butter, salt to taste. */
    val appleCake = Recipe(
        id = "recipe-cake",
        title = "Torta di mele",
        servings = 4,
        steps = listOf("Sbatti le uova con lo zucchero.", "Inforna a 180°C per 40 minuti."),
        ingredients = listOf(
            line("Farina", 300.0, MeasureUnit.GRAM, 0),
            line("Uova", 2.0, MeasureUnit.EGG, 1),
            line("Burro", 120.0, MeasureUnit.GRAM, 2),
            line("Sale", null, MeasureUnit.TO_TASTE, 3),
        ),
        tags = emptyList(),
    )
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.scale

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

class RecipeScalerServingsTest {

    private val scaler = TestScalerFactory.create()

    @Test
    fun `scaling from four to six servings gives a factor of one point five`() {
        val result = scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByServings(6.0))
        val scaled = (result as ScaleResult.Success).scaled

        assertThat(scaled.factor).isWithin(1e-9).of(1.5)
        assertThat(scaled.servings).isWithin(1e-9).of(6.0)
        assertThat(scaled.lines.first { it.lineId == "line-Farina" }.scaledQty).isWithin(1e-9).of(450.0)
        assertThat(scaled.lines.first { it.lineId == "line-Burro" }.scaledQty).isWithin(1e-9).of(180.0)
    }

    @Test
    fun `approximate ingredients pass through unscaled`() {
        val result = scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByServings(8.0))
        val salt = (result as ScaleResult.Success).scaled.lines.first { it.lineId == "line-Sale" }

        assertThat(salt.isScaled).isFalse()
        assertThat(salt.scaledQty).isNull()
        assertThat(salt.displayUnit).isEqualTo(MeasureUnit.TO_TASTE)
    }

    @Test
    fun `a plain factor scales every scalable line`() {
        val result = scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByFactor(0.5))
        val scaled = (result as ScaleResult.Success).scaled

        assertThat(scaled.factor).isWithin(1e-9).of(0.5)
        assertThat(scaled.lines.first { it.lineId == "line-Farina" }.scaledQty).isWithin(1e-9).of(150.0)
    }

    @Test
    fun `a non positive factor is rejected`() {
        val result = scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByFactor(0.0))
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.NonPositiveFactor)
    }

    @Test
    fun `scaling by servings fails when the recipe has no servings`() {
        val noServings = TestRecipes.appleCake.copy(servings = null)
        val result = scaler.scale(noServings, ScaleConstraint.ByServings(6.0))
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.NoServingsDefined)
    }

    @Test
    fun `an empty recipe is rejected`() {
        val empty = TestRecipes.appleCake.copy(ingredients = emptyList())
        val result = scaler.scale(empty, ScaleConstraint.ByFactor(2.0))
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.EmptyRecipe)
    }
}
```

Add `TestScalerFactory.create()` in the fixture file — it wires `DefaultRecipeScaler` with
`DefaultUnitConverter`, `QuantityFormatter` and the fake `UnitNamer` from Task 5.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:domain:test --tests '*RecipeScalerServingsTest*'`
Expected: FAIL — unresolved reference `ScaleConstraint`.

- [ ] **Step 4: Write the API types**

Copy the declarations from spec §5.1 verbatim into `ScaleConstraint.kt` and `ScaleResult.kt`
(`ScaleConstraint`, `AvailableAmount`, `Leftover`, `ScaleResult`, `ScaledRecipe`, `ScaledLine`,
`ScaleWarning`, `SnapOption`, `ScaleError`), plus:

```kotlin
fun interface RecipeScaler {
    fun scale(recipe: Recipe, constraint: ScaleConstraint): ScaleResult
}
```

- [ ] **Step 5: Implement `DefaultRecipeScaler` for the two simple constraints**

```kotlin
class DefaultRecipeScaler(
    private val converter: UnitConverter,
    private val formatter: QuantityFormatter,
) : RecipeScaler {

    override fun scale(recipe: Recipe, constraint: ScaleConstraint): ScaleResult {
        if (recipe.ingredients.isEmpty()) return ScaleResult.Failure(ScaleError.EmptyRecipe)
        val factor = when (constraint) {
            is ScaleConstraint.ByFactor -> constraint.factor
            is ScaleConstraint.ByServings -> {
                val base = recipe.servings ?: return ScaleResult.Failure(ScaleError.NoServingsDefined)
                constraint.target / base
            }
            else -> TODO("Tasks 7 and 9")   // replaced before this task is considered done
        }
        if (factor <= 0.0 || !factor.isFinite()) return ScaleResult.Failure(ScaleError.NonPositiveFactor)
        return ScaleResult.Success(buildScaledRecipe(recipe, factor))
    }

    private fun buildScaledRecipe(recipe: Recipe, factor: Double): ScaledRecipe { /* … */ }
}
```

`buildScaledRecipe` maps every line: approximate lines keep `scaledQty = null` and
`isScaled = false`; scalable lines multiply `quantity` by `factor` and run through
`formatter.format`. `servings` is `recipe.servings?.times(factor)`. Warnings stay empty until
Task 8.

**The `TODO(...)` branch must not survive Task 9.** If work stops before then, the status file
records it.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :core:domain:test --tests '*RecipeScalerServingsTest*'`
Expected: PASS, 6 tests.

---

## Task 7: `ByIngredient` constraint

**Files:**
- Modify: `core/domain/src/main/kotlin/…/core/domain/scale/DefaultRecipeScaler.kt`
- Test: `core/domain/src/test/kotlin/…/core/domain/scale/RecipeScalerByIngredientTest.kt`

**Interfaces:**
- Consumes: everything from Task 6.
- Produces: no new types; `ScaleConstraint.ByIngredient` now resolves to a factor.

- [ ] **Step 1: Write the failing test**

```kotlin
class RecipeScalerByIngredientTest {

    private val scaler = TestScalerFactory.create()

    @Test
    fun `fixing two eggs where the recipe wants two keeps the factor at one`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByIngredient("line-Uova", 2.0, MeasureUnit.EGG),
        )
        assertThat((result as ScaleResult.Success).scaled.factor).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `fixing three eggs scales the whole recipe up by one point five`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByIngredient("line-Uova", 3.0, MeasureUnit.EGG),
        )
        val scaled = (result as ScaleResult.Success).scaled
        assertThat(scaled.factor).isWithin(1e-9).of(1.5)
        assertThat(scaled.lines.first { it.lineId == "line-Farina" }.scaledQty).isWithin(1e-9).of(450.0)
    }

    @Test
    fun `the constraint may be expressed in another unit of the same category`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByIngredient("line-Farina", 0.6, MeasureUnit.KILOGRAM),
        )
        assertThat((result as ScaleResult.Success).scaled.factor).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `constraining an approximate ingredient is rejected`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByIngredient("line-Sale", 2.0, MeasureUnit.PINCH),
        )
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.ConstraintOnApproximateUnit)
    }

    @Test
    fun `constraining in an incompatible unit is rejected`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByIngredient("line-Farina", 300.0, MeasureUnit.MILLILITRE),
        )
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.IncompatibleUnit)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:domain:test --tests '*ByIngredientTest*'`
Expected: FAIL — `NotImplementedError` from the `TODO` branch.

- [ ] **Step 3: Implement the branch**

```kotlin
is ScaleConstraint.ByIngredient -> {
    val line = recipe.ingredients.firstOrNull { it.id == constraint.lineId }
        ?: return ScaleResult.Failure(ScaleError.IncompatibleUnit)
    if (!line.unit.isScalable || !constraint.unit.isScalable) {
        return ScaleResult.Failure(ScaleError.ConstraintOnApproximateUnit)
    }
    val requested = converter.convert(constraint.qty, constraint.unit, line.unit)
        ?: return ScaleResult.Failure(ScaleError.IncompatibleUnit)
    val original = line.quantity ?: return ScaleResult.Failure(ScaleError.IncompatibleUnit)
    requested / original
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:domain:test --tests '*ByIngredientTest*'`
Expected: PASS, 5 tests.

---

## Task 8: Discrete warnings and snap options

**Files:**
- Modify: `DefaultRecipeScaler.kt`
- Create: `core/domain/src/main/kotlin/…/core/domain/scale/DiscreteAnalyser.kt`
- Test: `core/domain/src/test/kotlin/…/core/domain/scale/DiscreteWarningTest.kt`

**Interfaces:**
- Consumes: Task 6 types.
- Produces: `DiscreteAnalyser.analyse(lines, factor): Pair<List<ScaleWarning>, List<SnapOption>>`,
  used by the scaler and reused by the UI to render snap chips.

- [ ] **Step 1: Write the failing test**

```kotlin
class DiscreteWarningTest {

    private val scaler = TestScalerFactory.create()

    /** Recipe serving 2 with 3 eggs: at ×1.5 it needs 4.5 eggs. */
    private val eggRecipe = TestRecipes.appleCake.copy(
        servings = 2,
        ingredients = listOf(TestRecipes.line("Uova", 3.0, MeasureUnit.EGG)),
    )

    @Test
    fun `a non integer discrete quantity raises a warning`() {
        val scaled = (scaler.scale(eggRecipe, ScaleConstraint.ByFactor(1.5)) as ScaleResult.Success).scaled
        val warning = scaled.warnings.filterIsInstance<ScaleWarning.NonIntegerDiscrete>().single()

        assertThat(warning.lineId).isEqualTo("line-Uova")
        assertThat(warning.exact).isWithin(1e-9).of(4.5)
    }

    @Test
    fun `snap options offer both the floor and the ceiling with their own factors`() {
        val scaled = (scaler.scale(eggRecipe, ScaleConstraint.ByFactor(1.5)) as ScaleResult.Success).scaled
        val targets = scaled.snapSuggestions.map { it.targetQty }

        assertThat(targets).containsExactly(4.0, 5.0)
        assertThat(scaled.snapSuggestions.first { it.targetQty == 4.0 }.resultingFactor)
            .isWithin(1e-9).of(4.0 / 3.0)
        assertThat(scaled.snapSuggestions.first { it.targetQty == 5.0 }.resultingFactor)
            .isWithin(1e-9).of(5.0 / 3.0)
    }

    @Test
    fun `values within five percent of an integer snap silently`() {
        val scaled = (scaler.scale(eggRecipe, ScaleConstraint.ByFactor(1.01)) as ScaleResult.Success).scaled

        assertThat(scaled.warnings).isEmpty()
        assertThat(scaled.lines.single().scaledQty).isWithin(1e-9).of(3.0)
    }

    @Test
    fun `a discrete quantity never rounds down to zero`() {
        val scaled = (scaler.scale(eggRecipe, ScaleConstraint.ByFactor(0.1)) as ScaleResult.Success).scaled

        assertThat(scaled.lines.single().scaledQty).isAtLeast(1.0)
        assertThat(scaled.warnings.filterIsInstance<ScaleWarning.NonIntegerDiscrete>()).isNotEmpty()
    }

    @Test
    fun `quantities below the measurable threshold raise TooSmallToMeasure`() {
        val yeast = TestRecipes.appleCake.copy(
            ingredients = listOf(TestRecipes.line("Lievito", 4.0, MeasureUnit.GRAM)),
        )
        val scaled = (scaler.scale(yeast, ScaleConstraint.ByFactor(0.1)) as ScaleResult.Success).scaled

        assertThat(scaled.warnings.filterIsInstance<ScaleWarning.TooSmallToMeasure>()).hasSize(1)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:domain:test --tests '*DiscreteWarningTest*'`
Expected: FAIL — `warnings` is empty.

- [ ] **Step 3: Implement `DiscreteAnalyser` and wire it in**

```kotlin
class DiscreteAnalyser {

    fun analyse(lines: List<ScaledLine>, originals: List<RecipeIngredient>): Analysis { /* … */ }

    data class Analysis(val warnings: List<ScaleWarning>, val snaps: List<SnapOption>)

    companion object { const val SNAP_TOLERANCE = 0.05 }
}
```

Rules: for each discrete line, compare `scaledQty` with `round(scaledQty)`; if the relative
difference is within `SNAP_TOLERANCE`, replace the value with the rounded one and emit nothing;
otherwise emit `NonIntegerDiscrete(lineId, exact)` and one `SnapOption` per `floor`/`ceil` (skipping
zero), each carrying `resultingFactor = target / originalQty`. Clamp any discrete result below 1 to
1 and still emit the warning. For continuous lines below `QuantityFormatter.MEASURABLE_THRESHOLD`,
emit `TooSmallToMeasure`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:domain:test --tests '*DiscreteWarningTest*'`
Expected: PASS, 5 tests.

---

## Task 9: `ByAvailability` — "with what I have"

**Files:**
- Modify: `DefaultRecipeScaler.kt` (removes the last `TODO`)
- Test: `core/domain/src/test/kotlin/…/core/domain/scale/RecipeScalerAvailabilityTest.kt`

**Interfaces:**
- Consumes: Tasks 6–8.
- Produces: `ScaledRecipe.bottleneckLineId` and `ScaledRecipe.leftovers` populated.

- [ ] **Step 1: Write the failing test**

```kotlin
class RecipeScalerAvailabilityTest {

    private val scaler = TestScalerFactory.create()

    @Test
    fun `the limiting ingredient decides the factor`() {
        // Cake serves 4: 300 g flour, 2 eggs. Have 3 eggs (x1.5) and 400 g flour (x1.33).
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByAvailability(
                listOf(
                    AvailableAmount("line-Uova", 3.0, MeasureUnit.EGG),
                    AvailableAmount("line-Farina", 400.0, MeasureUnit.GRAM),
                ),
            ),
        )
        val scaled = (result as ScaleResult.Success).scaled

        assertThat(scaled.factor).isWithin(1e-9).of(400.0 / 300.0)
        assertThat(scaled.bottleneckLineId).isEqualTo("line-Farina")
    }

    @Test
    fun `leftovers are reported for every non limiting ingredient`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByAvailability(
                listOf(
                    AvailableAmount("line-Uova", 3.0, MeasureUnit.EGG),
                    AvailableAmount("line-Farina", 400.0, MeasureUnit.GRAM),
                ),
            ),
        )
        val leftover = (result as ScaleResult.Success).scaled.leftovers.single()

        assertThat(leftover.lineId).isEqualTo("line-Uova")
        assertThat(leftover.qty).isWithin(1e-9).of(3.0 - 2.0 * (400.0 / 300.0))
    }

    @Test
    fun `achievable servings are reported as a fraction`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByAvailability(listOf(AvailableAmount("line-Farina", 400.0, MeasureUnit.GRAM))),
        )
        assertThat((result as ScaleResult.Success).scaled.servings).isWithin(1e-9).of(4.0 * 400.0 / 300.0)
    }

    @Test
    fun `approximate ingredients are ignored when computing the factor`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByAvailability(
                listOf(
                    AvailableAmount("line-Sale", 1.0, MeasureUnit.PINCH),
                    AvailableAmount("line-Farina", 600.0, MeasureUnit.GRAM),
                ),
            ),
        )
        assertThat((result as ScaleResult.Success).scaled.factor).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `an empty availability list is rejected`() {
        val result = scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByAvailability(emptyList()))
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.NonPositiveFactor)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:domain:test --tests '*AvailabilityTest*'`
Expected: FAIL — `NotImplementedError`.

- [ ] **Step 3: Implement the branch**

For each `AvailableAmount`: skip lines whose unit is approximate; convert the available quantity into
the line's unit (skip when `null`); candidate factor = available / original. The final factor is the
minimum of the candidates, and its line is `bottleneckLineId`. Leftovers are computed for every
other supplied amount as `available - original * factor`, dropped when ≤ 0. An empty candidate list
yields `ScaleError.NonPositiveFactor`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:domain:test --tests '*AvailabilityTest*'`
Expected: PASS, 5 tests. Confirm no `TODO(` remains in `DefaultRecipeScaler.kt`.

---

## Task 10: Oven advisory

**Files:**
- Create: `core/domain/src/main/kotlin/…/core/domain/scale/BakingAdvisor.kt`
- Modify: `DefaultRecipeScaler.kt`
- Test: `core/domain/src/test/kotlin/…/core/domain/scale/BakingAdvisorTest.kt`

**Interfaces:**
- Consumes: `Recipe`, `Tag`, `ScaleWarning`.
- Produces: `BakingAdvisor.advise(recipe, factor): ScaleWarning.BakingTimeCaution?` and the constant
  `BakingAdvisor.OVEN_TAG_KEY = "oven"`, used by the tag seeding in Task 11.

- [ ] **Step 1: Write the failing test**

```kotlin
class BakingAdvisorTest {

    private val advisor = BakingAdvisor()
    private val ovenTag = Tag(id = "tag-oven", key = "oven", name = null, isBuiltIn = true)
    private val ovenRecipe = TestRecipes.appleCake.copy(tags = listOf(ovenTag))

    @Test
    fun `no advisory for a recipe without the oven tag`() {
        assertThat(advisor.advise(TestRecipes.appleCake, 2.0)).isNull()
    }

    @Test
    fun `no advisory inside the safe band`() {
        assertThat(advisor.advise(ovenRecipe, 1.2)).isNull()
        assertThat(advisor.advise(ovenRecipe, 0.8)).isNull()
    }

    @Test
    fun `advisory above the safe band`() {
        val warning = advisor.advise(ovenRecipe, 1.5)
        assertThat(warning).isNotNull()
        assertThat(warning!!.factor).isWithin(1e-9).of(1.5)
    }

    @Test
    fun `advisory below the safe band`() {
        assertThat(advisor.advise(ovenRecipe, 0.5)).isNotNull()
    }

    @Test
    fun `tin diameter ratio is the square root of the factor at constant depth`() {
        val warning = advisor.advise(ovenRecipe, 1.5)!!
        assertThat(warning.suggestedTinDiameterRatio).isWithin(1e-9).of(sqrt(1.5))
        // a 24 cm tin becomes roughly 29 cm
        assertThat(24 * warning.suggestedTinDiameterRatio).isWithin(0.5).of(29.4)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:domain:test --tests '*BakingAdvisorTest*'`
Expected: FAIL — unresolved reference `BakingAdvisor`.

- [ ] **Step 3: Implement the advisor**

```kotlin
class BakingAdvisor {

    fun advise(recipe: Recipe, factor: Double): ScaleWarning.BakingTimeCaution? {
        val isOven = recipe.tags.any { it.isBuiltIn && it.key == OVEN_TAG_KEY }
        if (!isOven) return null
        if (factor in SAFE_LOW..SAFE_HIGH) return null
        return ScaleWarning.BakingTimeCaution(
            factor = factor,
            suggestedTinDiameterRatio = sqrt(factor),
        )
    }

    companion object {
        const val OVEN_TAG_KEY = "oven"
        const val SAFE_LOW = 0.7
        const val SAFE_HIGH = 1.4
    }
}
```

Wire it into `buildScaledRecipe`: append the advisory to `warnings` when non-null.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:domain:test`
Expected: PASS, whole domain suite green.

- [ ] **Step 5: Update the status checklist**

Tick "Scaling engine (TDD)" and "Oven advisory rule".

---

## Task 11: `:core:database` — Room schema, DAOs and seeding

**Files:**
- Create: `core/database/build.gradle.kts`
- Create: entities `RecipeEntity.kt`, `RecipeIngredientEntity.kt`, `IngredientEntity.kt`, `TagEntity.kt`, `RecipeTagCrossRef.kt`, `ScaleVariantEntity.kt`, `ShoppingItemEntity.kt`
- Create: `ProPortionDatabase.kt`, `Converters.kt`, DAOs `RecipeDao.kt`, `IngredientDao.kt`, `TagDao.kt`, `ShoppingDao.kt`, and `DatabaseSeeder.kt`
- Test: `core/database/src/test/kotlin/…/RecipeDaoTest.kt` (Robolectric-free: use an in-memory database in an `androidTest`-style JVM test via `Room.inMemoryDatabaseBuilder` with `AndroidJUnit4`; run as `androidTest` if the environment cannot host it)
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `MeasureUnit` and the model classes.
- Produces: `ProPortionDatabase` (version 1) and DAO methods used by Task 13:
  `RecipeDao.observeAll(): Flow<List<RecipeWithRelations>>`,
  `RecipeDao.observeFiltered(query: String, tagIds: List<String>, ingredientIds: List<String>, ingredientCount: Int): Flow<List<RecipeWithRelations>>`,
  `RecipeDao.upsert(recipe: RecipeEntity, lines: List<RecipeIngredientEntity>, tagIds: List<String>)`,
  `IngredientDao.observeInUse(): Flow<List<IngredientEntity>>`,
  `IngredientDao.findByNormalisedName(name: String): IngredientEntity?`,
  `TagDao.observeAll(): Flow<List<TagEntity>>`.

- [ ] **Step 1: Write the failing DAO test**

```kotlin
@RunWith(AndroidJUnit4::class)
class RecipeDaoTest {

    private lateinit var db: ProPortionDatabase
    private lateinit var dao: RecipeDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ProPortionDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.recipeDao()
    }

    @After fun tearDown() = db.close()

    @Test
    fun `inserting a recipe with lines and tags reads it back whole`() = runTest { /* … */ }

    @Test
    fun `free text search matches the title`() = runTest { /* … */ }

    @Test
    fun `free text search matches an ingredient name`() = runTest { /* … */ }

    @Test
    fun `tag filter and ingredient filter combine with AND`() = runTest { /* … */ }

    @Test
    fun `a recipe matches an ingredient filter only when it contains every selected ingredient`() = runTest { /* … */ }

    @Test
    fun `deleting a recipe cascades to lines, cross refs and variants but keeps ingredients`() = runTest { /* … */ }

    @Test
    fun `built in tags are seeded exactly once`() = runTest { /* … */ }
}
```

Fill each body with a concrete arrange/act/assert using the fixtures from Task 6 mapped to entities;
the bodies are written out in full during implementation, not left as comments.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: FAIL — unresolved reference `ProPortionDatabase`.

- [ ] **Step 3: Implement entities, DAOs and the database**

Key points:
- Every entity has `@PrimaryKey val id: String`.
- `IngredientEntity` includes `@ColumnInfo(name = "density_g_per_ml") val densityGramsPerMl: Double? = null` — v2 preparation, unused.
- Foreign keys with `onDelete = CASCADE` from lines, cross-refs and variants to `RecipeEntity`; **no** cascade from lines to `IngredientEntity` (`onDelete = RESTRICT`).
- Indices on `recipeId`, `ingredientId`, `normalisedName` (unique).
- `Converters` serialises `List<String>` (steps, sourceRecipeIds) as JSON and `MeasureUnit` as its name.
- The ingredient filter query uses `GROUP BY recipe.id HAVING COUNT(DISTINCT ingredientId) = :ingredientCount`.
- `DatabaseSeeder` inserts the nine built-in tags from spec §3.2 — including `oven`, keyed to `BakingAdvisor.OVEN_TAG_KEY` — inside an `onCreate` callback, and is idempotent.
- `exportSchema = true` with `room.schemaLocation` pointing at `core/database/schemas`, so migration tests have a baseline from release one.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: PASS, 7 tests.

- [ ] **Step 5: Update the status checklist**

Tick "Room schema, DAOs, migrations, seed of built-in tags".

---

## Task 12: `:core:datastore`, `:core:data` and repositories

**Files:**
- Create: `core/datastore/…/UserPreferencesDataSource.kt`, `core/datastore/build.gradle.kts`
- Create: `core/data/…/RecipeRepositoryImpl.kt`, `IngredientRepositoryImpl.kt`, `TagRepositoryImpl.kt`, `ShoppingRepositoryImpl.kt`, `PreferencesRepositoryImpl.kt`, mappers `EntityMappers.kt`, `core/data/build.gradle.kts`, Hilt module `DataModule.kt`
- Create: repository interfaces in `core/domain/…/repository/`
- Test: `core/data/src/test/kotlin/…/EntityMappersTest.kt`, `RecipeRepositoryImplTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: DAOs from Task 11, model from Task 3.
- Produces: `RecipeRepository`, `IngredientRepository`, `TagRepository`, `ShoppingRepository`,
  `PreferencesRepository` — all declared in `:core:domain`, all returning `Flow` for reads and
  `suspend` for writes. Feature ViewModels in later plans depend only on these.

- [ ] **Step 1: Declare the repository interfaces in the domain**

```kotlin
interface RecipeRepository {
    fun observeRecipes(filter: RecipeFilter): Flow<List<Recipe>>
    fun observeRecipe(id: String): Flow<Recipe?>
    suspend fun upsert(recipe: Recipe): String
    suspend fun delete(id: String)
    suspend fun markCooked(id: String, at: Long)
    suspend fun setFavourite(id: String, favourite: Boolean)
}

data class RecipeFilter(
    val query: String = "",
    val tagIds: List<String> = emptyList(),
    val ingredientIds: List<String> = emptyList(),
    val sort: RecipeSort = RecipeSort.RECENT,
)

enum class RecipeSort { RECENT, ALPHABETICAL, MOST_COOKED }
```

Plus `IngredientRepository.observeInUse()`, `observeAll()`, `findOrCreate(name, defaultUnit)`;
`TagRepository.observeAll()`, `create(name)`, `delete(id)`;
`ShoppingRepository.observeItems()`, `addScaled(lines, recipeId)`, `setChecked(id, checked)`,
`clearChecked()`, `clearAll()`;
`PreferencesRepository.observePreferences()`, `setThemeMode(...)`, `setDynamicColour(...)`,
`setLanguage(...)`.

- [ ] **Step 2: Write the failing mapper test**

```kotlin
class EntityMappersTest {

    @Test
    fun `a recipe survives a round trip through entities`() {
        val original = TestRecipes.appleCake
        val restored = original.toEntities().toDomain()
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `a null quantity on an approximate line survives the round trip`() { /* … */ }

    @Test
    fun `density is preserved even though v1 never sets it`() {
        val ingredient = TestRecipes.ingredient("Farina").copy(densityGramsPerMl = 0.55)
        assertThat(ingredient.toEntity().toDomain().densityGramsPerMl).isEqualTo(0.55)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: FAIL — unresolved reference `toEntities`.

- [ ] **Step 4: Implement mappers, repositories and the Hilt module**

`DataModule` binds `RecipeRepositoryImpl` to `RecipeRepository`, `DefaultUnitConverter` to
`UnitConverter`, `NoDensityRepository` to `DensityRepository` (the single line v2 will change), and
provides `DefaultRecipeScaler`, `QuantityFormatter`, `BakingAdvisor`, and the Room database.

`ShoppingRepositoryImpl.addScaled` merges by ingredient using `UnitConverter`: when the existing
item's unit and the incoming unit share a category, sum in the base unit and keep the more readable
display unit; otherwise insert a separate item.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest :core:database:testDebugUnitTest :core:domain:test`
Expected: PASS.

- [ ] **Step 6: Update the status checklist**

Tick "Repositories" and close out Phase 2.

---

## Task 13: `:core:designsystem` — palette, type, theme

**Files:**
- Create: `core/designsystem/build.gradle.kts`
- Create: `…/theme/Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`, `Motion.kt`
- Test: `core/designsystem/src/test/kotlin/…/ColorTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Produces: `ProPortionTheme(darkTheme, dynamicColor, content)` — every feature wraps its previews
  and `:app` wraps the whole UI in it.

- [ ] **Step 1: Define the palette**

```kotlin
val Pistachio = Color(0xFFA8D5BA)
val Apricot   = Color(0xFFF4B393)
val Butter    = Color(0xFFF2D48A)
val Blueberry = Color(0xFFA9BEEA)
val Amber     = Color(0xFFB4762B)      // advisories only
val GreenInk  = Color(0xFF1D2621)
```

Build `lightColorScheme`/`darkColorScheme` from these with Material 3 roles, and expose the chart
series as a separate `ProPortionChartColors` list so the dashboard donut does not borrow semantic
roles.

- [ ] **Step 2: Write the theme**

```kotlin
@Composable
fun ProPortionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,          // Material You on by default, spec §9
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> ProPortionDarkColors
        else -> ProPortionLightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = ProPortionTypography, shapes = ProPortionShapes, content = content)
}
```

- [ ] **Step 3: Write the contrast test**

```kotlin
class ColorTest {
    @Test
    fun `brand palette meets WCAG AA for body text in both schemes`() {
        assertThat(contrastRatio(ProPortionLightColors.onSurface, ProPortionLightColors.surface)).isAtLeast(4.5)
        assertThat(contrastRatio(ProPortionDarkColors.onSurface, ProPortionDarkColors.surface)).isAtLeast(4.5)
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS. Adjust the tonal values until the assertion holds — do not weaken the assertion.

- [ ] **Step 5: Update the status checklist**

Tick "Design system: pastel palette, typography, Material You".

---

## Task 14: App icon

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`
- Modify: `AndroidManifest.xml`

- [ ] **Step 1: Draw the vectors**

Background: solid `#A8D5BA`. Foreground: a **clipart-style 3D pie chart** inside the inner 66 dp
safe zone of the 108 dp canvas — an ellipse seen in perspective (roughly 2:1 width to height) with
an extruded side wall about 10 dp deep. Three wedges on the top face (`#F2D48A`, `#F4B393`,
`#EFB0C4`), each side wall a darker shade of its wedge (multiply roughly 0.8). Thick rounded
outlines (~2.5 dp, `#1D2621`), flat fills, no gradients. Three candles with flames stand on the top
face, with the same outline weight so the clipart reads as one object.
Monochrome: the same silhouette in a single colour.

- [ ] **Step 2: Wire the adaptive icon**

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

- [ ] **Step 3: Verify the masks**

Run: `./gradlew :app:assembleDebug`, install, and check the launcher icon under circular, squircle
and teardrop masks plus the themed-icon setting on Android 13+.

- [ ] **Step 4: Update the status checklist**

Tick "Adaptive + monochrome app icon".

---

## Task 15: Navigation shell with four empty tabs

**Files:**
- Create: `feature/home`, `feature/recipes`, `feature/cook`, `feature/shopping`, `feature/settings` modules, each with a single placeholder composable
- Create: `app/…/navigation/ProPortionNavHost.kt`, `TopLevelDestination.kt`
- Modify: `MainActivity.kt`, `settings.gradle.kts`, `app/build.gradle.kts`, `values/strings.xml`, `values-it/strings.xml`

**Interfaces:**
- Produces: `TopLevelDestination` enum (HOME, RECIPES, SHOPPING, SETTINGS) with route, icon and
  label resource; each feature module exposes `fun NavGraphBuilder.xScreen(...)`. Later plans fill
  the screens without touching `:app`.

- [ ] **Step 1: Create the feature modules**

Each `feature/*/build.gradle.kts` applies `proportion.android.library`,
`proportion.android.library.compose`, `proportion.hilt`, and depends on `:core:domain`,
`:core:designsystem`, `:core:ui`.

- [ ] **Step 2: Define the destinations**

```kotlin
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Rounded.Home),
    RECIPES("recipes", R.string.nav_recipes, Icons.Rounded.MenuBook),
    SHOPPING("shopping", R.string.nav_shopping, Icons.Rounded.ShoppingCart),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Settings),
}
```

Add every label to `values/strings.xml` (English) and `values-it/strings.xml` (Italian). No literal
strings in composables.

- [ ] **Step 3: Build the scaffold and NavHost**

`MainActivity` sets `ProPortionTheme { ProPortionApp() }`; `ProPortionApp` renders a `Scaffold` with
a `NavigationBar` driven by `TopLevelDestination.entries` and a `NavHost` whose start destination is
`HOME`. Tab switching preserves each tab's back stack (`saveState`/`restoreState`, `launchSingleTop`).

- [ ] **Step 4: Write the navigation test**

```kotlin
@Test
fun `tapping a tab shows that destination`() {
    composeTestRule.setContent { ProPortionTheme { ProPortionApp() } }
    composeTestRule.onNodeWithText("Ricettario").performClick()
    composeTestRule.onNodeWithTag("recipes_screen").assertIsDisplayed()
}
```

Run: `./gradlew :app:connectedDebugAndroidTest` (or the equivalent instrumented run).
Expected: PASS.

- [ ] **Step 5: Update the status checklist**

Tick "Navigation with four empty tabs" and close out Phase 1.

---

## Task 16: README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Write it**

English, short: what ProPortion is (one paragraph), a screenshot placeholder, build instructions
(`./gradlew :app:assembleDebug`, JDK 17, Android SDK 36), the module map in five lines, where the
docs live (`docs/public`, `docs/manual`, `docs/private`), licence, author **Marco Zanetti**. Link to
the docs instead of duplicating them. No employer reference anywhere.

- [ ] **Step 2: Verify**

Re-read against the repo: every command in the README must actually work as written.

- [ ] **Step 3: Update the status checklist**

Tick "README.md". Phases 1 and 2 are then complete; the next plan covers Phase 3.

---

## Self-review notes

- **Spec coverage for phases 1–2:** §3 model → Tasks 3, 11; §4 units → Tasks 3, 4, 5; §5 engine →
  Tasks 6–10; §6 modules → Tasks 1, 3, 4, 11, 12, 13, 15; §9 design system → Tasks 13, 14; §10
  localisation groundwork → Tasks 1, 15; §12 v2 readiness → Tasks 3, 4, 11, 12; §14 testing → every
  task, plus Task 2 for CI. Spec §7 (screens), §8 (transfer) and the remaining §11 documentation are
  Phases 3–7 and belong to later plans.
- **Deliberately deferred inside this plan:** `.proportion` serialisation (`:core:transfer`) is not
  built here — it is the opening task of the Phase 5 plan.
