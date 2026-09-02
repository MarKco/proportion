package com.ilsecondodasinistra.proportion.feature.cook

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.feature.cook.navigation.encodeForRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CookingModeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun cookingModeViewModel(
        recipe: Recipe = CookTestData.cake,
        constraint: ScaleConstraint? = null,
        recipes: FakeRecipeRepository = FakeRecipeRepository(listOf(recipe)),
    ): CookingModeViewModel {
        val args = buildMap<String, Any?> {
            put("recipeId", recipe.id)
            constraint?.let { put("constraint", it.encodeForRoute()) }
        }
        return CookingModeViewModel(
            savedStateHandle = SavedStateHandle(args),
            recipeRepository = recipes,
            scaler = testScaler(),
            formatter = testFormatter(),
            time = TimeProvider { 5_000L },
        )
    }

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
}
