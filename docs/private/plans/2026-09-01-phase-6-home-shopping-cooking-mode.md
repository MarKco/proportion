# ProPortion Phase 6 — Home, Shopping and Cooking Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app stops being a recipe box and becomes a kitchen companion: a dashboard that says what the library holds and what to cook, one persistent shopping list fed by the scale screen, and a cooking mode you can follow with floury hands.

**Architecture:** No new persistence. Every number on the dashboard is derived, in `:core:domain`, from the one flow of recipes the repositories already expose — pure functions, tested without Android, so composables keep doing zero arithmetic. The shopping list already has its repository (phase 2, `ShoppingRepository.addScaled` merges compatible units); phase 6 only builds its screen and the two entry points that fill it. Cooking mode is a second route inside `:feature:cook`, because it is the same scaling carried into a different presentation — a feature module must never depend on another feature module.

**Tech Stack:** Compose (Canvas for the donut, `AnimatedContent` for the reshuffle), Hilt, `WindowCompat` / `FLAG_KEEP_SCREEN_ON`, kotlinx.serialization for the constraint carried on the route, Turbine + Truth + Robolectric for tests.

**Spec:** `docs/private/specs/2026-09-01-proportion-v1-design.md` (§3.1 default variant, §7.4 last paragraph, §7.5, §7.6, §7.7)

## Global Constraints

- Package root `com.ilsecondodasinistra.proportion`; author Marco Zanetti; **never mention his employer** anywhere.
- **Never `git commit` or `git push`** — Marco does that himself. Where another plan would commit, this one ends the task with the full check and an update to `docs/private/IMPLEMENTATION-STATUS.md`.
- Tests first for `:core:domain`; that module may not import `android.*` (`NoAndroidDependencyTest` asserts it).
- No hardcoded user-facing strings: every new string in `values/strings.xml` (English) **and** `values-it/strings.xml` (Italian), in the module that uses it.
- No arithmetic in composables — quantities and counts arrive already computed in the UI state.
- Features never depend on other features. Routes stay type-safe (`@Serializable` route keys).
- minSdk 26, compileSdk/targetSdk 36, JVM target 17. detekt `maxIssues: 0`.
- Full check, run from the repo root with JDK 21:
  ```bash
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  ./gradlew detekt testDebugUnitTest :core:model:test :core:domain:test :core:transfer:test assembleDebug
  ```

---

## File structure

```
core/domain/.../dashboard/
  DashboardSummary.kt        the value the dashboard draws: counts, slices, picks
  DashboardSummariser.kt     pure List<Recipe> -> DashboardSummary
  RecipePicker.kt            "what shall I cook?", seeded and testable
core/designsystem/.../component/
  DonutChart.kt              a reusable animated donut; knows nothing about recipes
feature/home/
  HomeUiState.kt             the four cards, already formatted
  HomeViewModel.kt           one flow in, one state out
  HomeScreen.kt              the cards
  HomeCards.kt               one composable per card, kept out of the screen file
feature/shopping/
  ShoppingUiState.kt
  ShoppingViewModel.kt
  ShoppingScreen.kt
core/transfer/
  ShoppingListFormatter.kt   the list as shareable plain text
feature/cook/
  CookingModeScreen.kt       big type, checkable steps, ingredients within reach
  CookingModeViewModel.kt
  CookingModeUiState.kt
  navigation/CookRouteKey.kt (modified) second route, carrying the constraint
feature/recipes/detail/
  RecipeDetailViewModel.kt   (modified) default variant applied on open
  RecipeDetailScreen.kt      (modified) "showing: For 6 - view original" banner, cook counter
```

Rationale: `DashboardSummariser` exists as its own class rather than living in the ViewModel because it is the only place phase 6 does arithmetic, and it is the part worth testing exhaustively. `DonutChart` sits in `:core:designsystem` because it is a drawing primitive, not a recipe concept.

---

## Task 1: `DashboardSummary` — every number the dashboard shows

**Files:**
- Create: `core/domain/src/main/kotlin/com/ilsecondodasinistra/proportion/core/domain/dashboard/DashboardSummary.kt`
- Create: `core/domain/src/main/kotlin/com/ilsecondodasinistra/proportion/core/domain/dashboard/DashboardSummariser.kt`
- Create: `core/domain/src/main/kotlin/com/ilsecondodasinistra/proportion/core/domain/dashboard/RecipePicker.kt`
- Test: `core/domain/src/test/kotlin/com/ilsecondodasinistra/proportion/core/domain/dashboard/DashboardSummariserTest.kt`
- Test: `core/domain/src/test/kotlin/com/ilsecondodasinistra/proportion/core/domain/dashboard/RecipePickerTest.kt`

**Interfaces:**
- Consumes: `Recipe` (`core.model`), `Tag.BUILT_IN_KEYS`.
- Produces:
  - `DashboardSummariser.summarise(recipes: List<Recipe>, topN: Int = 3): DashboardSummary`
  - `RecipePicker.pick(recipes: List<Recipe>, tagId: String?, excluding: String?, random: Random): Recipe?`
  - `CourseSlice(tagId, tagKey, count)`, and `DashboardSummary.COURSE_KEYS`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.Tag
import org.junit.Test

class DashboardSummariserTest {

    private val summariser = DashboardSummariser()

    private fun tag(key: String) = Tag(id = "tag-$key", key = key, name = null, isBuiltIn = true)

    private fun recipe(
        id: String,
        title: String = id,
        tags: List<Tag> = emptyList(),
        cookCount: Int = 0,
        lastCookedAt: Long? = null,
        favourite: Boolean = false,
        updatedAt: Long = 0L,
    ) = Recipe(
        id = id,
        title = title,
        servings = 4,
        steps = emptyList(),
        ingredients = emptyList(),
        tags = tags,
        isFavourite = favourite,
        cookCount = cookCount,
        lastCookedAt = lastCookedAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `an empty library summarises to zeros, not to nulls the UI has to guard`() {
        val summary = summariser.summarise(emptyList())

        assertThat(summary.recipeCount).isEqualTo(0)
        assertThat(summary.totalCooks).isEqualTo(0)
        assertThat(summary.favouriteCount).isEqualTo(0)
        assertThat(summary.courseSlices).isEmpty()
        assertThat(summary.continueCooking).isNull()
        assertThat(summary.mostCooked).isEmpty()
        assertThat(summary.favourites).isEmpty()
    }

    @Test
    fun `counts are the plain totals`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", cookCount = 3, favourite = true),
                recipe("b", cookCount = 1),
                recipe("c"),
            ),
        )

        assertThat(summary.recipeCount).isEqualTo(3)
        assertThat(summary.totalCooks).isEqualTo(4)
        assertThat(summary.favouriteCount).isEqualTo(1)
    }

    @Test
    fun `slices follow the course order, and empty courses are dropped`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", tags = listOf(tag("dessert"))),
                recipe("b", tags = listOf(tag("first_course"))),
                recipe("c", tags = listOf(tag("first_course"))),
            ),
        )

        assertThat(summary.courseSlices.map { it.tagKey }).containsExactly("first_course", "dessert").inOrder()
        assertThat(summary.courseSlices.first().count).isEqualTo(2)
    }

    @Test
    fun `a recipe with two course tags counts once in each slice`() {
        val summary = summariser.summarise(
            listOf(recipe("a", tags = listOf(tag("main_course"), tag("side_dish")))),
        )

        assertThat(summary.courseSlices.map { it.tagKey })
            .containsExactly("main_course", "side_dish").inOrder()
        assertThat(summary.courseSlices.map { it.count }).containsExactly(1, 1)
    }

    @Test
    fun `oven is not a course, so an oven-only recipe counts as uncategorised`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", tags = listOf(tag("oven"))),
                recipe("b", tags = listOf(Tag("t1", null, "nonna", isBuiltIn = false))),
            ),
        )

        assertThat(summary.courseSlices).isEmpty()
        assertThat(summary.uncategorisedCount).isEqualTo(2)
    }

    @Test
    fun `continue cooking is the most recently cooked recipe`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", lastCookedAt = 100L, cookCount = 1),
                recipe("b", lastCookedAt = 900L, cookCount = 1),
                recipe("c"),
            ),
        )

        assertThat(summary.continueCooking?.id).isEqualTo("b")
    }

    @Test
    fun `nothing cooked yet means no continue card`() {
        assertThat(summariser.summarise(listOf(recipe("a"))).continueCooking).isNull()
    }

    @Test
    fun `most cooked ignores never-cooked recipes, sorts by count then title, and caps at topN`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", title = "Zuppa", cookCount = 5),
                recipe("b", title = "Brodo", cookCount = 5),
                recipe("c", title = "Pane", cookCount = 2),
                recipe("d", title = "Torta", cookCount = 1),
                recipe("e", title = "Mai", cookCount = 0),
            ),
            topN = 3,
        )

        assertThat(summary.mostCooked.map { it.title }).containsExactly("Brodo", "Zuppa", "Pane").inOrder()
    }

    @Test
    fun `favourites come back most recently updated first, capped at topN`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", favourite = true, updatedAt = 10L),
                recipe("b", favourite = true, updatedAt = 30L),
                recipe("c", favourite = false, updatedAt = 99L),
            ),
            topN = 3,
        )

        assertThat(summary.favourites.map { it.id }).containsExactly("b", "a").inOrder()
    }
}
```

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.Tag
import kotlin.random.Random
import org.junit.Test

class RecipePickerTest {

    private val picker = RecipePicker()
    private val dessert = Tag(id = "tag-dessert", key = "dessert", name = null, isBuiltIn = true)

    private fun recipe(id: String, tags: List<Tag> = emptyList()) = Recipe(
        id = id, title = id, servings = 4, steps = emptyList(),
        ingredients = emptyList(), tags = tags,
    )

    @Test
    fun `an empty library has nothing to suggest`() {
        assertThat(picker.pick(emptyList(), tagId = null, excluding = null, random = Random(1))).isNull()
    }

    @Test
    fun `a tag filter restricts the candidates`() {
        val picked = picker.pick(
            listOf(recipe("a"), recipe("b", tags = listOf(dessert))),
            tagId = "tag-dessert",
            excluding = null,
            random = Random(1),
        )

        assertThat(picked?.id).isEqualTo("b")
    }

    @Test
    fun `a tag nothing carries suggests nothing rather than falling back`() {
        val picked = picker.pick(listOf(recipe("a")), tagId = "tag-dessert", excluding = null, random = Random(1))

        assertThat(picked).isNull()
    }

    @Test
    fun `reshuffling never lands on the recipe already showing`() {
        val recipes = listOf(recipe("a"), recipe("b"), recipe("c"))

        repeat(50) { seed ->
            val picked = picker.pick(recipes, tagId = null, excluding = "a", random = Random(seed))
            assertThat(picked?.id).isNotEqualTo("a")
        }
    }

    @Test
    fun `with a single candidate, reshuffling returns it rather than nothing`() {
        val picked = picker.pick(listOf(recipe("a")), tagId = null, excluding = "a", random = Random(1))

        assertThat(picked?.id).isEqualTo("a")
    }

    @Test
    fun `the same seed picks the same recipe`() {
        val recipes = listOf(recipe("a"), recipe("b"), recipe("c"))

        assertThat(picker.pick(recipes, null, null, Random(7))?.id)
            .isEqualTo(picker.pick(recipes, null, null, Random(7))?.id)
    }
}
```

- [ ] **Step 2: Run them and watch them fail.**

```bash
./gradlew :core:domain:test --tests "*dashboard*"
```
Expected: FAIL — unresolved reference `DashboardSummariser`.

- [ ] **Step 3: Implement the value and the two pure functions.**

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.ilsecondodasinistra.proportion.core.model.Recipe

/** One arc of the donut: how many recipes carry this course tag. */
data class CourseSlice(val tagId: String, val tagKey: String, val count: Int)

/**
 * Everything the dashboard draws, computed once. The screen renders this and calculates nothing:
 * that is what keeps the four cards consistent with each other on every recomposition.
 */
data class DashboardSummary(
    val recipeCount: Int = 0,
    val totalCooks: Int = 0,
    val favouriteCount: Int = 0,
    val courseSlices: List<CourseSlice> = emptyList(),
    val uncategorisedCount: Int = 0,
    val continueCooking: Recipe? = null,
    val mostCooked: List<Recipe> = emptyList(),
    val favourites: List<Recipe> = emptyList(),
) {
    companion object {
        /** The built-in tags that describe a course. `oven` is a technique, not a course. */
        val COURSE_KEYS = listOf(
            "appetizer", "first_course", "main_course",
            "side_dish", "dessert", "bread_and_leavened", "preserves", "drinks",
        )
    }
}

class DashboardSummariser {

    fun summarise(recipes: List<Recipe>, topN: Int = DEFAULT_TOP_N): DashboardSummary {
        if (recipes.isEmpty()) return DashboardSummary()

        val courseKeys = DashboardSummary.COURSE_KEYS
        val slices = courseKeys.mapNotNull { key ->
            val tagged = recipes.filter { recipe ->
                recipe.tags.any { it.isBuiltIn && it.key == key }
            }
            val tagId = tagged.firstNotNullOfOrNull { recipe ->
                recipe.tags.firstOrNull { it.isBuiltIn && it.key == key }?.id
            }
            if (tagged.isEmpty() || tagId == null) null
            else CourseSlice(tagId = tagId, tagKey = key, count = tagged.size)
        }

        return DashboardSummary(
            recipeCount = recipes.size,
            totalCooks = recipes.sumOf { it.cookCount },
            favouriteCount = recipes.count { it.isFavourite },
            courseSlices = slices,
            uncategorisedCount = recipes.count { recipe ->
                recipe.tags.none { it.isBuiltIn && it.key in courseKeys }
            },
            continueCooking = recipes
                .filter { it.lastCookedAt != null }
                .maxByOrNull { it.lastCookedAt ?: 0L },
            mostCooked = recipes
                .filter { it.cookCount > 0 }
                .sortedWith(compareByDescending<Recipe> { it.cookCount }.thenBy { it.title })
                .take(topN),
            favourites = recipes
                .filter { it.isFavourite }
                .sortedByDescending { it.updatedAt }
                .take(topN),
        )
    }

    private companion object {
        const val DEFAULT_TOP_N = 3
    }
}
```

```kotlin
package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.ilsecondodasinistra.proportion.core.model.Recipe
import kotlin.random.Random

/**
 * "What shall I cook?" — a random pick, optionally inside one tag.
 *
 * [excluding] is the recipe currently on screen: reshuffling that lands on the same card reads as a
 * broken button, so it is dropped from the candidates unless it is the only one.
 */
class RecipePicker {

    fun pick(
        recipes: List<Recipe>,
        tagId: String?,
        excluding: String?,
        random: Random,
    ): Recipe? {
        val matching = when (tagId) {
            null -> recipes
            else -> recipes.filter { recipe -> recipe.tags.any { it.id == tagId } }
        }
        if (matching.isEmpty()) return null

        val candidates = matching.filterNot { it.id == excluding }.ifEmpty { matching }
        return candidates[random.nextInt(candidates.size)]
    }
}
```

- [ ] **Step 4: Run the tests and detekt.**

```bash
./gradlew :core:domain:test detekt
```
Expected: PASS.

- [ ] **Step 5: Update the checklist.** Mark the dashboard-summary line in `docs/private/IMPLEMENTATION-STATUS.md` as `[~]` under phase 6. Do not commit.

---

## Task 2: `DonutChart` — the drawing primitive

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/ilsecondodasinistra/proportion/core/designsystem/component/DonutChart.kt`
- Test: `core/designsystem/src/test/kotlin/com/ilsecondodasinistra/proportion/core/designsystem/component/DonutChartTest.kt`

**Interfaces:**
- Produces: `DonutSlice(value: Int, color: Color, label: String)` and
  `@Composable fun DonutChart(slices: List<DonutSlice>, modifier: Modifier, centreLabel: String, centreCaption: String)`, plus the pure helper `sweepAngles(values: List<Int>): List<Float>`.

- [ ] **Step 1: Write the failing test.** Only the angle maths is worth asserting; the drawing itself is verified on the device in Task 8.

```kotlin
package com.ilsecondodasinistra.proportion.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DonutChartTest {

    @Test
    fun `equal values split the circle evenly`() {
        assertThat(sweepAngles(listOf(1, 1, 1, 1)))
            .containsExactly(90f, 90f, 90f, 90f).inOrder()
    }

    @Test
    fun `angles are proportional to the values`() {
        val angles = sweepAngles(listOf(3, 1))

        assertThat(angles[0]).isWithin(0.01f).of(270f)
        assertThat(angles[1]).isWithin(0.01f).of(90f)
    }

    @Test
    fun `no values means no arcs rather than a division by zero`() {
        assertThat(sweepAngles(emptyList())).isEmpty()
        assertThat(sweepAngles(listOf(0, 0))).containsExactly(0f, 0f).inOrder()
    }
}
```

- [ ] **Step 2: Run it and watch it fail.**

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "*DonutChartTest*"
```

- [ ] **Step 3: Implement.**

```kotlin
package com.ilsecondodasinistra.proportion.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionMotion

data class DonutSlice(val value: Int, val color: Color, val label: String)

/** Proportional sweeps for one turn of the circle. Empty or all-zero input draws nothing. */
fun sweepAngles(values: List<Int>): List<Float> {
    val total = values.sum()
    if (total <= 0) return values.map { 0f }
    return values.map { it * FULL_TURN / total }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f

/**
 * Recipes per course. The donut animates from nothing on first composition, which is the one place
 * in the app where motion carries meaning: the library filling up.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centreLabel: String,
    centreCaption: String,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 160.dp,
    thickness: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = ProPortionMotion.emphasised(),
        label = "donut",
    )
    val sweeps = sweepAngles(slices.map { it.value })

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            var start = START_ANGLE
            sweeps.forEachIndexed { index, sweep ->
                drawArc(
                    color = slices[index].color,
                    startAngle = start,
                    sweepAngle = sweep * progress,
                    useCenter = false,
                    style = Stroke(width = thickness.toPx()),
                )
                start += sweep
            }
        }
        Box(contentAlignment = Alignment.Center) {
            Text(text = centreLabel, style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            text = centreCaption,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
```

If `ProPortionMotion` does not expose `emphasised()`, read `core/designsystem/.../theme/ProPortionMotion.kt` and use whatever emphasised spec it already defines rather than adding a new one.

- [ ] **Step 4: Run the test and detekt.**

```bash
./gradlew :core:designsystem:testDebugUnitTest detekt
```

---

## Task 3: The Home dashboard

**Files:**
- Create: `feature/home/src/main/kotlin/.../home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeCards.kt`
- Modify: `feature/home/src/main/kotlin/.../home/HomeScreen.kt`, `HomeRoute.kt`, `feature/home/build.gradle.kts`
- Create: `feature/home/src/main/res/values/strings.xml`, `feature/home/src/main/res/values-it/strings.xml`
- Test: `feature/home/src/test/kotlin/.../home/HomeViewModelTest.kt`, `HomeScreenTest.kt`, `HomeTestDoubles.kt`

**Interfaces:**
- Consumes: `DashboardSummariser`, `RecipePicker`, `RecipeRepository.observeRecipes()`, `TagRepository.observeAll()`, `ScaleVariantRepository.observeForRecipe(id)`.
- Produces: `HomeRoute` gains navigation callbacks —
  `fun NavGraphBuilder.homeScreen(onRecipeClick: (String) -> Unit, onCook: (String) -> Unit, onAddRecipe: () -> Unit)`.

- [ ] **Step 1: Extend the module's dependencies.** `feature/home/build.gradle.kts` needs the same test stack the cook feature uses:

```kotlin
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
    testImplementation(libs.truth)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Copy `MainDispatcherRule` from `feature/cook/src/test/kotlin/.../cook/` into `feature/home/src/test/kotlin/.../home/` (it is four lines; a shared test module is not worth its own Gradle module here).

- [ ] **Step 2: Write the failing ViewModel tests.**

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        recipes: List<Recipe> = HomeTestData.library,
        tags: List<Tag> = HomeTestData.tags,
    ) = HomeViewModel(
        recipeRepository = FakeRecipeRepository(recipes),
        tagRepository = FakeTagRepository(tags),
        variantRepository = FakeScaleVariantRepository(),
        summariser = DashboardSummariser(),
        picker = RecipePicker(),
        random = Random(1),
    )

    @Test
    fun `an empty library asks for the first recipe instead of drawing empty cards`() = runTest {
        viewModel(recipes = emptyList()).uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.isLoading).isFalse()
            assertThat(state.isEmpty).isTrue()
        }
    }

    @Test
    fun `the numbers card reports the library`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.recipeCount).isEqualTo(HomeTestData.library.size)
            assertThat(state.totalCooks).isEqualTo(HomeTestData.library.sumOf { it.cookCount })
            assertThat(state.donutSlices).isNotEmpty()
        }
    }

    @Test
    fun `continue cooking names the last cooked recipe and its default variant`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.continueCooking?.recipeId).isEqualTo(HomeTestData.lastCookedId)
            assertThat(state.continueCooking?.variantLabel).isEqualTo("Per 6")
        }
    }

    @Test
    fun `the suggestion changes when the user reshuffles`() = runTest {
        val model = viewModel()
        model.uiState.test {
            advanceUntilIdle()
            val first = expectMostRecentItem().suggestion?.recipeId

            model.onReshuffle()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().suggestion?.recipeId).isNotEqualTo(first)
        }
    }

    @Test
    fun `filtering the suggestion by tag only suggests recipes carrying it`() = runTest {
        val model = viewModel()
        model.onSuggestionTagChange(HomeTestData.dessertTagId)
        model.uiState.test {
            advanceUntilIdle()
            val suggested = expectMostRecentItem().suggestion?.recipeId

            assertThat(HomeTestData.dessertIds).contains(suggested)
        }
    }

    @Test
    fun `a tag with no recipes says so rather than suggesting something else`() = runTest {
        val model = viewModel()
        model.onSuggestionTagChange(HomeTestData.emptyTagId)
        model.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.suggestion).isNull()
            assertThat(state.suggestionUnavailable).isTrue()
        }
    }
}
```

`HomeTestDoubles.kt` holds `HomeTestData` (a small library: one dessert, one first course, one never-cooked recipe, one favourite, `lastCookedId` cooked most recently) and reuses the fake repository shapes from `feature/recipes/src/test/kotlin/.../TestDoubles.kt` — read that file and copy the pattern rather than inventing a new one.

- [ ] **Step 3: Run and watch them fail.**

```bash
./gradlew :feature:home:testDebugUnitTest
```

- [ ] **Step 4: Implement `HomeUiState`.**

```kotlin
package com.ilsecondodasinistra.proportion.feature.home

/** A recipe as the dashboard cards show it: nothing but text and an id to navigate with. */
data class RecipeCardItem(
    val recipeId: String,
    val title: String,
    val cookCount: Int = 0,
    val isFavourite: Boolean = false,
)

/** The last cooked recipe, plus the scaling it was cooked at when one is saved as default. */
data class ContinueCooking(
    val recipeId: String,
    val title: String,
    val variantLabel: String?,
)

data class DonutSliceUi(val tagKey: String, val count: Int, val colorIndex: Int)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,

    val recipeCount: Int = 0,
    val totalCooks: Int = 0,
    val favouriteCount: Int = 0,
    val donutSlices: List<DonutSliceUi> = emptyList(),
    val uncategorisedCount: Int = 0,

    val continueCooking: ContinueCooking? = null,
    val mostCooked: List<RecipeCardItem> = emptyList(),
    val favourites: List<RecipeCardItem> = emptyList(),

    val suggestion: RecipeCardItem? = null,
    val suggestionTagId: String? = null,
    val suggestionUnavailable: Boolean = false,
    val suggestionTags: List<TagChipItem> = emptyList(),
)

data class TagChipItem(val id: String, val key: String?, val name: String?)
```

- [ ] **Step 5: Implement `HomeViewModel`.** One `combine` of recipes and tags feeds the summariser; the last-cooked recipe's default variant is looked up with `flatMapLatest`. `random` is injected so the tests are deterministic — provide it from Hilt with `@Provides fun random(): Random = Random.Default` in the home module's own DI file, or default the constructor parameter to `Random.Default` and keep the module free of DI wiring. Prefer the constructor default: less machinery.

Key behaviours the tests above pin down:
- `isEmpty` is true only once loading has finished and the library is empty.
- `onReshuffle()` re-picks with `excluding = current suggestion id`.
- `onSuggestionTagChange(tagId)` re-picks inside the tag and sets `suggestionUnavailable` when nothing matches.

- [ ] **Step 6: Implement the cards** in `HomeCards.kt` — one composable each: `NumbersCard` (counts + `DonutChart` + a legend built from `tagLabel()` in `:core:ui`), `ContinueCookingCard`, `MostCookedCard` (most cooked and favourites in one card, two labelled columns), `SuggestionCard` (tag filter chips, the pick inside `AnimatedContent`, a reshuffle button). Empty state: `UiStateScaffold` from `:core:ui` with a call to action that routes to the editor.

- [ ] **Step 7: Strings.** English in `values/strings.xml`, Italian in `values-it/strings.xml`. At minimum: `home_title`, `home_empty_title`, `home_empty_body`, `home_empty_action`, `home_numbers_title`, `home_recipes_count`, `home_cooks_count`, `home_favourites_count`, `home_uncategorised`, `home_continue_title`, `home_continue_showing`, `home_most_cooked_title`, `home_favourites_title`, `home_suggestion_title`, `home_suggestion_reshuffle`, `home_suggestion_none`, `home_suggestion_all_tags`, `home_cook_action`. Use `<plurals>` for the three counts — Italian and English both need them.

- [ ] **Step 8: Compose tests.**

```kotlin
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the empty state offers to add the first recipe`() {
        var added = false
        composeRule.setContent {
            HomeScreen(state = HomeUiState(isLoading = false, isEmpty = true), onAddRecipe = { added = true })
        }

        composeRule.onNodeWithTag("home_empty_action").performClick()
        assertThat(added).isTrue()
    }

    @Test
    fun `tapping the suggestion opens that recipe`() {
        var opened: String? = null
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    isLoading = false,
                    suggestion = RecipeCardItem("r1", "Torta di mele"),
                ),
                onRecipeClick = { opened = it },
            )
        }

        composeRule.onNodeWithText("Torta di mele").performClick()
        assertThat(opened).isEqualTo("r1")
    }
}
```

Give `HomeScreen` a stateless overload taking `HomeUiState` plus lambdas, with a stateful `HomeRoute` collecting from the ViewModel — the same split `CookScreen` already uses.

- [ ] **Step 9: Wire the navigation.** `homeScreen(onRecipeClick, onCook, onAddRecipe)` in `HomeRoute.kt`, and in `app/.../navigation/ProPortionApp.kt`:

```kotlin
homeScreen(
    onRecipeClick = navController::navigateToRecipeDetail,
    onCook = navController::navigateToCook,
    onAddRecipe = navController::navigateToNewRecipe,
)
```

- [ ] **Step 10: Full check, then update `IMPLEMENTATION-STATUS.md`** — tick "Dashboard cards". Do not commit.

---

## Task 4: The shopping list as shareable text

**Files:**
- Create: `core/transfer/src/main/kotlin/.../transfer/ShoppingListFormatter.kt`
- Test: `core/transfer/src/test/kotlin/.../transfer/ShoppingListFormatterTest.kt`

**Interfaces:**
- Produces: `ShoppingListFormatter.format(items: List<ShoppingItem>, strings: ShoppingListStrings, formatter: QuantityFormatter): String`, and `ShoppingListStrings(title: String, checkedTitle: String, attribution: String)`.

- [ ] **Step 1: Write the failing tests.** The formatter follows `PlainTextFormatter`'s conventions exactly: aligned column, attribution last.

```kotlin
class ShoppingListFormatterTest {

    private val strings = ShoppingListStrings(
        title = "Shopping list",
        checkedTitle = "Already bought",
        attribution = "Made with ProPortion",
    )

    @Test
    fun `unchecked items come first, aligned, with their amounts`() {
        val text = ShoppingListFormatter.format(
            listOf(item("Farina", 500.0, MeasureUnit.GRAM), item("Uova", 3.0, MeasureUnit.PIECE)),
            strings,
            testFormatter(),
        )

        assertThat(text).contains("- Farina  500 g")
        assertThat(text).contains("- Uova    3")
    }

    @Test
    fun `checked items move to their own section rather than disappearing`() {
        val text = ShoppingListFormatter.format(
            listOf(item("Farina", 500.0, MeasureUnit.GRAM), item("Sale", 5.0, MeasureUnit.GRAM, checked = true)),
            strings,
            testFormatter(),
        )

        assertThat(text.indexOf("Already bought")).isGreaterThan(text.indexOf("Farina"))
        assertThat(text).contains("Sale")
    }

    @Test
    fun `an item with no measurable amount still lists its name`() {
        val text = ShoppingListFormatter.format(
            listOf(item("Prezzemolo", null, MeasureUnit.TO_TASTE)),
            strings,
            testFormatter(),
        )

        assertThat(text).contains("Prezzemolo")
    }

    @Test
    fun `an empty list produces the title and nothing that looks like a bullet`() {
        val text = ShoppingListFormatter.format(emptyList(), strings, testFormatter())

        assertThat(text).contains("Shopping list")
        assertThat(text).doesNotContain("- ")
    }

    @Test
    fun `the attribution closes the message`() {
        val text = ShoppingListFormatter.format(listOf(item("Farina", 500.0, MeasureUnit.GRAM)), strings, testFormatter())

        assertThat(text.trim().endsWith("Made with ProPortion")).isTrue()
    }
}
```

Read `core/transfer/src/test/kotlin/.../PlainTextFormatterTest.kt` first and reuse its `testFormatter()` and `MeasureUnit` names verbatim — do not invent unit constants that do not exist in `MeasureUnit`.

- [ ] **Step 2: Run and watch them fail.**

```bash
./gradlew :core:transfer:test --tests "*ShoppingListFormatterTest*"
```

- [ ] **Step 3: Implement**, mirroring `PlainTextFormatter`'s padding approach.

- [ ] **Step 4:** `./gradlew :core:transfer:test detekt`.

---

## Task 5: The shopping screen

**Files:**
- Create: `feature/shopping/src/main/kotlin/.../shopping/ShoppingUiState.kt`, `ShoppingViewModel.kt`
- Modify: `feature/shopping/src/main/kotlin/.../shopping/ShoppingScreen.kt`, `ShoppingRoute.kt`, `feature/shopping/build.gradle.kts`
- Create: `feature/shopping/src/main/res/values/strings.xml`, `values-it/strings.xml`
- Test: `feature/shopping/src/test/kotlin/.../shopping/ShoppingViewModelTest.kt`, `ShoppingScreenTest.kt`, `ShoppingTestDoubles.kt`

**Interfaces:**
- Consumes: `ShoppingRepository` (already in `:core:domain`), `ShoppingListFormatter`, `RecipeSharing.shareText` from `:core:ui`.
- Produces: `ShoppingUiState`, and a screen with three actions: check an item, clear checked, clear all, plus share.

- [ ] **Step 1: Dependencies.** Add `implementation(projects.core.ui)`, `implementation(projects.core.transfer)`, `implementation(libs.androidx.compose.material.icons.extended)` and the same test stack as Task 3.

- [ ] **Step 2: Failing ViewModel tests.**

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeShoppingRepository(ShoppingTestData.items)
    private fun viewModel() = ShoppingViewModel(repository, testFormatter())

    @Test
    fun `an empty list says the list is empty instead of showing an empty column`() = runTest {
        ShoppingViewModel(FakeShoppingRepository(emptyList()), testFormatter()).uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.isLoading).isFalse()
            assertThat(state.items).isEmpty()
            assertThat(state.isEmpty).isTrue()
        }
    }

    @Test
    fun `unchecked items are listed before checked ones`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            val firstChecked = state.items.indexOfFirst { it.isChecked }
            val lastUnchecked = state.items.indexOfLast { !it.isChecked }
            assertThat(firstChecked).isGreaterThan(lastUnchecked)
        }
    }

    @Test
    fun `checking an item writes through to the repository`() = runTest {
        val model = viewModel()
        model.uiState.test { advanceUntilIdle(); expectMostRecentItem() }

        model.onCheckedChange(ShoppingTestData.flourId, true)
        advanceUntilIdle()

        assertThat(repository.checked).containsEntry(ShoppingTestData.flourId, true)
    }

    @Test
    fun `clear checked removes only the checked ones`() = runTest {
        val model = viewModel()
        model.onClearChecked()
        advanceUntilIdle()

        assertThat(repository.clearCheckedCalls).isEqualTo(1)
        assertThat(repository.clearAllCalls).isEqualTo(0)
    }

    @Test
    fun `clear all asks for confirmation before emptying the list`() = runTest {
        val model = viewModel()

        model.onClearAllRequested()
        advanceUntilIdle()
        assertThat(model.uiState.value.confirmClearAll).isTrue()
        assertThat(repository.clearAllCalls).isEqualTo(0)

        model.onClearAllConfirmed()
        advanceUntilIdle()
        assertThat(repository.clearAllCalls).isEqualTo(1)
        assertThat(model.uiState.value.confirmClearAll).isFalse()
    }

    @Test
    fun `the share text lists every item`() = runTest {
        val model = viewModel()
        model.uiState.test { advanceUntilIdle(); expectMostRecentItem() }

        val text = model.shareText(ShoppingTestData.strings)

        assertThat(text).contains("Farina")
        assertThat(text).contains("Uova")
    }

    @Test
    fun `an item shows which recipes put it there`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val flour = expectMostRecentItem().items.first { it.id == ShoppingTestData.flourId }

            assertThat(flour.sourceCount).isEqualTo(2)
        }
    }
}
```

- [ ] **Step 3: Run and watch them fail.**

- [ ] **Step 4: Implement the state and ViewModel.**

```kotlin
data class ShoppingRow(
    val id: String,
    val name: String,
    val amountText: String,
    val isChecked: Boolean,
    val sourceCount: Int,
)

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val items: List<ShoppingRow> = emptyList(),
    val checkedCount: Int = 0,
    val confirmClearAll: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
```

The amount text comes from `QuantityFormatter` in the ViewModel — never in the composable. Items with a null quantity render their name alone.

- [ ] **Step 5: Implement the screen.** A `LazyColumn` of rows with a leading `Checkbox`, the name, the amount trailing, and a caption naming how many recipes contributed when `sourceCount > 1`. Checked rows are struck through and dimmed. A top app bar carries the overflow: share, clear checked (enabled only when `checkedCount > 0`), clear all (confirmation dialog). Empty state through `UiStateScaffold`.

- [ ] **Step 6: Compose tests** — the empty state renders; toggling a checkbox reports the id; the clear-all dialog appears and reports confirmation.

- [ ] **Step 7: Strings** in both languages: `shopping_title`, `shopping_empty_title`, `shopping_empty_body`, `shopping_share`, `shopping_share_chooser`, `shopping_clear_checked`, `shopping_clear_all`, `shopping_clear_all_confirm_title`, `shopping_clear_all_confirm_body`, `shopping_from_recipes` (plural), `shopping_list_title`, `shopping_list_checked_title`, `shopping_attribution`.

- [ ] **Step 8: Full check, update `IMPLEMENTATION-STATUS.md`.**

---

## Task 6: Filling the list from the scale screen

**Files:**
- Modify: `feature/cook/src/main/kotlin/.../cook/CookViewModel.kt`, `CookUiState.kt`, `CookScreen.kt`
- Modify: `feature/cook/src/main/res/values/strings.xml`, `values-it/strings.xml`
- Modify: `feature/cook/src/test/kotlin/.../cook/CookTestDoubles.kt`, `CookViewModelTest.kt`

**Interfaces:**
- Consumes: `ShoppingRepository.addScaled(lines: List<ScaledLine>, recipeId: String)` — already implemented, already merges compatible units.
- Produces: `CookViewModel.onAddToShoppingList()`, and `CookUiState.shoppingMessage: ShoppingMessage?` where `ShoppingMessage` is `data class Added(val count: Int)` / `data object NothingToAdd`.

- [ ] **Step 1: Failing tests.**

```kotlin
@Test
fun `adding to the shopping list sends the scaled lines, not the originals`() = runTest {
    val shopping = FakeShoppingRepository()
    val model = viewModel(shopping = shopping)
    advanceUntilIdle()

    model.onFactorChange("2")
    advanceUntilIdle()
    model.onAddToShoppingList()
    advanceUntilIdle()

    val flour = shopping.added.single().lines.first { it.ingredientName == "Farina" }
    assertThat(flour.scaledQty).isWithin(1e-9).of(600.0)
}

@Test
fun `approximate lines are not added, because there is no amount to buy`() = runTest {
    val shopping = FakeShoppingRepository()
    val model = viewModel(shopping = shopping)
    advanceUntilIdle()

    model.onAddToShoppingList()
    advanceUntilIdle()

    assertThat(shopping.added.single().lines.none { !it.isScaled }).isTrue()
}

@Test
fun `the screen confirms how many lines went to the list`() = runTest {
    val model = viewModel(shopping = FakeShoppingRepository())
    advanceUntilIdle()

    model.onAddToShoppingList()
    advanceUntilIdle()

    assertThat(model.uiState.value.shoppingMessage).isInstanceOf(ShoppingMessage.Added::class.java)
}

@Test
fun `dismissing the confirmation clears it, so it does not reappear on rotation`() = runTest {
    val model = viewModel(shopping = FakeShoppingRepository())
    advanceUntilIdle()
    model.onAddToShoppingList()
    advanceUntilIdle()

    model.onShoppingMessageShown()

    assertThat(model.uiState.value.shoppingMessage).isNull()
}
```

`FakeShoppingRepository` records `added: List<AddCall>` where `AddCall(lines, recipeId)`; put it in `CookTestDoubles.kt` beside the existing fakes.

- [ ] **Step 2: Run and watch them fail.**

- [ ] **Step 3: Implement.** Inject `ShoppingRepository` into `CookViewModel`; `onAddToShoppingList()` passes the current `ScaledRecipe.lines` and the recipe id, then sets the message. The filtering of unscalable lines already happens inside `ShoppingRepository.addScaled` — do not duplicate it in the ViewModel; the test above asserts the observable outcome, not a second implementation.

- [ ] **Step 4: Wire the button** into the scaled-card actions in `CookScreen.kt`, beside "save as variant", and show the confirmation as a `Snackbar` whose text comes from `values/strings.xml` (`cook_add_to_shopping`, `cook_added_to_shopping` as a plural, `cook_nothing_to_add`).

- [ ] **Step 5:** `./gradlew :feature:cook:testDebugUnitTest detekt`.

---

## Task 7: Cooking mode

**Files:**
- Create: `feature/cook/src/main/kotlin/.../cook/CookingModeUiState.kt`, `CookingModeViewModel.kt`, `CookingModeScreen.kt`
- Modify: `feature/cook/src/main/kotlin/.../cook/navigation/CookRouteKey.kt`, `CookScreen.kt`
- Modify: `app/src/main/kotlin/.../navigation/ProPortionApp.kt`
- Modify: `feature/cook/src/main/res/values/strings.xml`, `values-it/strings.xml`
- Test: `feature/cook/src/test/kotlin/.../cook/CookingModeViewModelTest.kt`, `CookingModeScreenTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Serializable
  data class CookingModeRouteKey(val recipeId: String, val constraint: String? = null)

  fun NavController.navigateToCookingMode(recipeId: String, constraint: ScaleConstraint?)
  fun NavGraphBuilder.cookingModeScreen(onBack: () -> Unit)
  ```
  The constraint travels as URL-safe Base64 of its JSON, because raw JSON in a route argument is a
  reliable source of escaping bugs:
  ```kotlin
  internal fun ScaleConstraint.encodeForRoute(): String =
      Base64.getUrlEncoder().withoutPadding()
          .encodeToString(Json.encodeToString(this).toByteArray())

  internal fun String.decodeConstraint(): ScaleConstraint? = runCatching {
      Json.decodeFromString<ScaleConstraint>(String(Base64.getUrlDecoder().decode(this)))
  }.getOrNull()
  ```
  (`java.util.Base64` is available from API 26, which is the minSdk.)

- [ ] **Step 1: Failing ViewModel tests.**

```kotlin
@Test
fun `it opens on the scaling it was entered with`() = runTest {
    val model = cookingModeViewModel(constraint = ScaleConstraint.ByFactor(2.0))
    advanceUntilIdle()

    val flour = model.uiState.value.ingredients.first { it.name == "Farina" }
    assertThat(flour.amountText).isEqualTo("600 g")
}

@Test
fun `without a constraint it cooks the recipe as written`() = runTest {
    val model = cookingModeViewModel(constraint = null)
    advanceUntilIdle()

    assertThat(model.uiState.value.factor).isWithin(1e-9).of(1.0)
}

@Test
fun `entering cooking mode counts as having cooked it`() = runTest {
    val recipes = FakeRecipeRepository(listOf(CookTestData.cake))
    cookingModeViewModel(recipes = recipes)
    advanceUntilIdle()

    assertThat(recipes.cookedAt[CookTestData.cake.id]).isEqualTo(5_000L)
}

@Test
fun `it counts the cook once, however many times the state re-emits`() = runTest {
    val recipes = FakeRecipeRepository(listOf(CookTestData.cake))
    val model = cookingModeViewModel(recipes = recipes)
    advanceUntilIdle()
    model.onStepChecked(0, true)
    advanceUntilIdle()

    assertThat(recipes.markCookedCalls).isEqualTo(1)
}

@Test
fun `checking a step is remembered, and unchecking undoes it`() = runTest {
    val model = cookingModeViewModel()
    advanceUntilIdle()

    model.onStepChecked(1, true)
    assertThat(model.uiState.value.steps[1].isDone).isTrue()

    model.onStepChecked(1, false)
    assertThat(model.uiState.value.steps[1].isDone).isFalse()
}

@Test
fun `progress reports the steps done out of the total`() = runTest {
    val model = cookingModeViewModel()
    advanceUntilIdle()
    model.onStepChecked(0, true)

    assertThat(model.uiState.value.doneCount).isEqualTo(1)
    assertThat(model.uiState.value.steps).hasSize(CookTestData.cake.steps.size)
}

@Test
fun `a recipe without steps still shows its ingredients rather than an empty screen`() = runTest {
    val model = cookingModeViewModel(recipe = CookTestData.jam.copy(steps = emptyList()))
    advanceUntilIdle()

    assertThat(model.uiState.value.steps).isEmpty()
    assertThat(model.uiState.value.ingredients).isNotEmpty()
}
```

`FakeRecipeRepository` needs `cookedAt: Map<String, Long>` and `markCookedCalls: Int`; extend the existing fake in `CookTestDoubles.kt` rather than adding a second one.

- [ ] **Step 2: Run and watch them fail.**

- [ ] **Step 3: Implement `CookingModeUiState`.**

```kotlin
data class CookingStep(val index: Int, val text: String, val isDone: Boolean)

data class CookingIngredient(val name: String, val amountText: String)

data class CookingModeUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val factor: Double = 1.0,
    val servingsText: String? = null,
    val steps: List<CookingStep> = emptyList(),
    val ingredients: List<CookingIngredient> = emptyList(),
    val showIngredients: Boolean = false,
) {
    val doneCount: Int get() = steps.count { it.isDone }
}
```

- [ ] **Step 4: Implement `CookingModeViewModel`.** It loads the recipe, decodes the constraint from the route (falling back to `ByFactor(1.0)`), runs `RecipeScaler` once, formats every amount with `QuantityFormatter`, and calls `recipeRepository.markCooked(id, time.now())` exactly once, guarded by a `private var counted = false`, in `init`. Checked steps live in the state only: a half-finished recipe is not worth a schema migration, and the screen is not meant to be left.

- [ ] **Step 5: Implement `CookingModeScreen`.**
  - Keep the screen awake for as long as the screen is composed:
    ```kotlin
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    ```
  - Type one step up from the detail screen (`headlineSmall` for steps, `titleLarge` for the title), generous vertical spacing, 48 dp minimum touch targets.
  - Steps as a `LazyColumn` of large checkable rows; a done step dims and strikes through.
  - Ingredients one tap away: a `BottomSheetScaffold` or a `ModalBottomSheet` toggled by a persistent button showing the scaled amounts.
  - A top bar with a close action and "3 / 8" progress.
  - Test tags: `cooking_mode_screen`, `cooking_step_<index>`, `cooking_ingredients_button`.

- [ ] **Step 6: Compose tests** — the steps render and report their index and checked state; the ingredients button opens the sheet; `keepScreenOn` is true while composed (assert via `composeRule.onRoot()` presence plus a direct check on the `View` if Robolectric allows it; if it does not, drop that assertion and verify it on the device in Task 9 instead — do not fake a passing test).

- [ ] **Step 7: Wire it up.** A "Start cooking" button on the scaled card in `CookScreen.kt` calls `onCookingMode(recipeId, currentConstraint)`; `ProPortionApp` adds `cookingModeScreen(onBack = { navController.popBackStack() })`. The bottom bar must stay hidden here — it already is, since only top-level destinations show it.

- [ ] **Step 8: Strings** in both languages: `cooking_mode_start`, `cooking_mode_title`, `cooking_mode_progress`, `cooking_mode_ingredients`, `cooking_mode_close`, `cooking_mode_no_steps`.

- [ ] **Step 9:** `./gradlew :feature:cook:testDebugUnitTest detekt`, then update `IMPLEMENTATION-STATUS.md`.

---

## Task 8: The default variant, and the cook counter where it belongs

Spec §3.1 promises that a recipe with a default variant opens already scaled, with a way back to the original. Nothing implements it yet; phase 6 is where it belongs, because it is the same idea as "continue cooking" on the dashboard.

**Files:**
- Modify: `feature/recipes/src/main/kotlin/.../recipes/detail/RecipeDetailViewModel.kt`, `RecipeDetailUiState.kt`, `RecipeDetailScreen.kt`
- Modify: `feature/recipes/src/main/res/values/strings.xml`, `values-it/strings.xml`
- Modify: `feature/recipes/src/test/kotlin/.../recipes/RecipeDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `ScaleVariantRepository.observeForRecipe`, `readConstraint`, `RecipeScaler`.
- Produces: `RecipeDetailUiState.showingVariant: ShowingVariant?` (`label`, `variantId`) and `RecipeDetailViewModel.onShowOriginal()` / `onShowVariant(id)`.

- [ ] **Step 1: Failing tests.**

```kotlin
@Test
fun `a recipe with a default variant opens at that scaling`() = runTest {
    val model = viewModel(variants = listOf(defaultVariantForSix))
    advanceUntilIdle()
    val state = model.uiState.value

    assertThat(state.showingVariant?.label).isEqualTo("Per 6")
    assertThat(state.ingredients.first { it.name == "Farina" }.amountText).isEqualTo("450 g")
}

@Test
fun `a recipe without a default variant opens as written`() = runTest {
    val model = viewModel(variants = emptyList())
    advanceUntilIdle()

    assertThat(model.uiState.value.showingVariant).isNull()
}

@Test
fun `view original goes back to the recipe as entered`() = runTest {
    val model = viewModel(variants = listOf(defaultVariantForSix))
    advanceUntilIdle()

    model.onShowOriginal()
    advanceUntilIdle()

    assertThat(model.uiState.value.showingVariant).isNull()
    assertThat(model.uiState.value.ingredients.first { it.name == "Farina" }.amountText).isEqualTo("300 g")
}

@Test
fun `a variant whose constraint no longer applies falls back to the original instead of failing`() = runTest {
    val model = viewModel(variants = listOf(variantOnDeletedLine))
    advanceUntilIdle()

    assertThat(model.uiState.value.showingVariant).isNull()
    assertThat(model.uiState.value.ingredients).isNotEmpty()
}

@Test
fun `the detail reports how many times the recipe was cooked`() = runTest {
    val model = viewModel(recipe = cake.copy(cookCount = 4))
    advanceUntilIdle()

    assertThat(model.uiState.value.cookCount).isEqualTo(4)
}
```

- [ ] **Step 2: Run and watch them fail.**

- [ ] **Step 3: Implement.** On load, pick the variant with `isDefault = true`, read its constraint, run the scaler; on `ScaleResult.Failure` fall back to the unscaled recipe and leave `showingVariant` null — a recipe edited after its variant was saved must still open.

- [ ] **Step 4: The banner.** A slim assist bar above the ingredients: "Showing: Per 6 · View original", with the second half as the action. When the original is showing and variants exist, each variant chip can switch to it. Strings: `detail_showing_variant`, `detail_view_original`, `detail_cook_count` (plural).

- [ ] **Step 5:** `./gradlew :feature:recipes:testDebugUnitTest detekt`.

---

## Task 9: Verify the phase on the device, then write it down

**Files:**
- Modify: `docs/private/IMPLEMENTATION-STATUS.md`
- Modify: `docs/private/architecture.md` (the dashboard's derive-don't-store decision, and cooking mode as a second route in `:feature:cook`)

- [ ] **Step 1: Full check.**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew detekt testDebugUnitTest :core:model:test :core:domain:test :core:transfer:test assembleDebug
```
Expected: BUILD SUCCESSFUL, every test green. Record the new test count.

- [ ] **Step 2: Install and walk through it.**

```bash
./gradlew installDebug
```

Walk the whole phase on the Fairphone 3, in this order, and note anything that misbehaves:
1. Open Home on an empty library — the invitation shows, not four empty cards.
2. Add two recipes, tag one `dessert` and one `first_course` — the donut animates and the legend matches.
3. Cook one at ×2, add it to the shopping list, then add a second recipe's flour — the two flours merge into one line.
4. Check an item, share the list, clear checked, clear all with the confirmation.
5. From the scale screen, start cooking mode — the screen stays awake, the type is big, steps check off, the ingredient sheet shows the scaled amounts.
6. Back on Home, "Continue cooking" names the recipe just cooked, and "Most cooked" has moved.
7. Reshuffle "What shall I cook?" a dozen times — it never shows the same card twice in a row, and a tag with no recipes says so.
8. Save a variant as default, reopen the recipe — it opens scaled with "View original" working.

- [ ] **Step 3: Screenshots for phase 7.** Capture Home, the shopping list and cooking mode now, while the flows are fresh:

```bash
adb shell screencap -p /sdcard/home.png && adb pull /sdcard/home.png docs/private/screenshots/
```
(Create `docs/private/screenshots/` if it does not exist. The manual's final screenshots are captured in phase 7, once the strings are final; these are working references.)

- [ ] **Step 4: Update `docs/private/IMPLEMENTATION-STATUS.md`** — tick all three phase 6 lines, refresh "Last updated", the test count, and rewrite the "What is next" paragraph to point at phase 7 and its plan.

- [ ] **Step 5: Stop.** Do not commit and do not push: report what changed and let Marco commit.

---

## Self-review notes

- **Spec coverage.** §7.6 dashboard → Tasks 1, 2, 3. §7.7 shopping list → Tasks 4, 5, and the entry point in Task 6. §7.5 cooking mode → Task 7. §3.1 default variant and the cook counter → Task 8. §7.4's "add the scaled quantities to the shopping list" and "enter cooking mode" → Tasks 6 and 7.
- **Deliberately not built.** Manual entry into the shopping list (not in the spec; `addScaled` is the only documented way in), per-item quantity editing, and persisting checked steps across a process death — all v2 or later.
- **The one deliberate overlap.** `markCooked` already fires when the scaled card is opened (phase 4) and now also on entering cooking mode. Both are guarded per ViewModel instance, so one cooking session counts once; if the walkthrough in Task 9 shows a single session counting twice, keep the cooking-mode call and drop the one in `CookViewModel.onShowCard`, because §7.5 attributes the count to cooking mode.
- **Type consistency.** `ScaledLine.ingredientName`, `ScaledLine.scaledQty`, `ScaledLine.isScaled`, `ScaleConstraint.ByFactor(factor)`, `Tag.BUILT_IN_KEYS`, `RecipeSharing.shareText` and `ShoppingRepository.addScaled(lines, recipeId)` are all used here exactly as they are already defined in the codebase; nothing in this plan renames an existing symbol.
