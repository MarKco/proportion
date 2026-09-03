package com.ilsecondodasinistra.proportion.feature.cook

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.domain.unit.DensityRequirement
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CookViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val variants = FakeScaleVariantRepository()

    private fun viewModel(
        recipe: Recipe = CookTestData.cake,
        recipes: FakeRecipeRepository = FakeRecipeRepository(
            listOf(CookTestData.cake, CookTestData.ovenCake, CookTestData.eggRecipe, CookTestData.jam),
        ),
        shopping: FakeShoppingRepository = FakeShoppingRepository(),
        ingredients: FakeIngredientRepository = FakeIngredientRepository(),
    ) = CookViewModel(
        savedStateHandle = SavedStateHandle(mapOf("recipeId" to recipe.id)),
        recipeRepository = recipes,
        variantRepository = variants,
        shoppingRepository = shopping,
        ingredientRepository = ingredients,
        scaler = testScaler(),
        formatter = testFormatter(),
    )

    private fun lineOf(state: CookUiState, name: String) =
        state.lines.first { it.name == name }

    @Test
    fun `it opens at the recipe's own size`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.factor).isWithin(1e-9).of(1.0)
            assertThat(state.servings).isWithin(1e-9).of(4.0)
            assertThat(state.mode).isEqualTo(CookMode.SERVINGS)
            assertThat(lineOf(state, "Farina").scaledText).isEqualTo("300 g")
        }
    }

    @Test
    fun `raising the servings rescales every line`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onServingsChange(6)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.factor).isWithin(1e-9).of(1.5)
            assertThat(lineOf(state, "Farina").scaledText).isEqualTo("450 g")
            assertThat(lineOf(state, "Uova").scaledText).isEqualTo("3 uova")
        }
    }

    @Test
    fun `an approximate ingredient is left alone`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onServingsChange(8)
            advanceUntilIdle()

            val salt = lineOf(expectMostRecentItem(), "Sale")
            assertThat(salt.isScaled).isFalse()
            assertThat(salt.scaledText).isEqualTo("q.b.")
        }
    }

    @Test
    fun `fixing an ingredient rescales the rest`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onModeChange(CookMode.INGREDIENT)
            vm.onIngredientSelected("line-uova")
            vm.onIngredientQuantityChange("3")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.factor).isWithin(1e-9).of(1.5)
            assertThat(lineOf(state, "Farina").scaledText).isEqualTo("450 g")
        }
    }

    @Test
    fun `a plain factor works and accepts a comma`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onModeChange(CookMode.FACTOR)
            vm.onFactorChange("1,5")
            advanceUntilIdle()

            assertThat(expectMostRecentItem().factor).isWithin(1e-9).of(1.5)
        }
    }

    @Test
    fun `pantry mode reports the bottleneck and the achievable servings`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onModeChange(CookMode.PANTRY)
            vm.onPantryAmountChange("line-uova", "3")     // allows x1.5
            vm.onPantryAmountChange("line-farina", "400") // allows x1.33
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.factor).isWithin(1e-9).of(400.0 / 300.0)
            assertThat(state.bottleneckLineId).isEqualTo("line-farina")
            assertThat(state.servings).isWithin(1e-9).of(4.0 * 400.0 / 300.0)
            assertThat(state.leftovers.map { it.lineId }).containsExactly("line-uova")
        }
    }

    @Test
    fun `an impractical discrete amount is flagged with its snaps`() = runTest {
        val vm = viewModel(recipe = CookTestData.eggRecipe)
        vm.uiState.test {
            advanceUntilIdle()
            vm.onServingsChange(3)   // x1.5 of 3 eggs = 4.5
            advanceUntilIdle()

            val eggs = lineOf(expectMostRecentItem(), "Uova")
            assertThat(eggs.hasWarning).isTrue()
            assertThat(eggs.snaps.map { it.option.targetQty }).containsExactly(4.0, 5.0)
        }
    }

    @Test
    fun `accepting a snap rescales the whole recipe and clears the warning`() = runTest {
        val vm = viewModel(recipe = CookTestData.eggRecipe)
        vm.uiState.test {
            advanceUntilIdle()
            vm.onServingsChange(3)
            advanceUntilIdle()
            val snap = lineOf(expectMostRecentItem(), "Uova").snaps.first { it.option.targetQty == 4.0 }

            vm.onSnapAccept(snap.option)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.factor).isWithin(1e-9).of(4.0 / 3.0)
            assertThat(state.mode).isEqualTo(CookMode.FACTOR)
            assertThat(lineOf(state, "Uova").hasWarning).isFalse()
            assertThat(lineOf(state, "Uova").scaledText).isEqualTo("4 uova")
        }
    }

    @Test
    fun `an oven recipe pushed too far raises the baking advisory`() = runTest {
        val vm = viewModel(recipe = CookTestData.ovenCake)
        vm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().ovenAdvisory).isNull()

            vm.onServingsChange(8) // x2
            advanceUntilIdle()

            val advisory = expectMostRecentItem().ovenAdvisory
            assertThat(advisory).isNotNull()
            assertThat(advisory!!.tinDiameterRatio).isWithin(1e-3).of(1.414)
        }
    }

    @Test
    fun `a recipe without servings reports why servings mode cannot work`() = runTest {
        val vm = viewModel(recipe = CookTestData.jam)
        vm.uiState.test {
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.mode).isEqualTo(CookMode.FACTOR)
            assertThat(state.servingsModeAvailable).isFalse()
        }
    }

    @Test
    fun `switching mode starts that mode from the current factor`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onServingsChange(6)
            advanceUntilIdle()

            vm.onModeChange(CookMode.FACTOR)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.factor).isWithin(1e-9).of(1.5)
            assertThat(state.factorInput).isEqualTo("1,5")
        }
    }

    @Test
    fun `saving a variant stores the constraint, not the numbers`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onServingsChange(6)
            advanceUntilIdle()

            vm.onSaveVariant("Per 6 persone", asDefault = false)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val (label, constraint) = variants.savedConstraints.single()
        assertThat(label).isEqualTo("Per 6 persone")
        assertThat(constraint).isEqualTo(ScaleConstraint.ByServings(6.0))
    }

    @Test
    fun `showing the scaled card does not mark the recipe cooked — cooking mode does that`() = runTest {
        val recipes = FakeRecipeRepository(listOf(CookTestData.cake))
        val vm = viewModel(recipes = recipes)
        vm.uiState.test {
            advanceUntilIdle()

            vm.onShowCard(true)
            vm.onShowCard(false)
            vm.onShowCard(true)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().showCard).isTrue()
        }

        assertThat(recipes.markCookedCalls).isEqualTo(0)
    }

    @Test
    fun `constraining in millilitres works when the ingredient has a known density`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onModeChange(CookMode.INGREDIENT)
            vm.onIngredientSelected("line-farina")
            vm.onIngredientUnitChange(MeasureUnit.MILLILITRE)
            vm.onIngredientQuantityChange("530")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.pendingDensityPrompt).isNull()
            // 530 ml x 0.53 g/ml = 280.9 g, against the recipe's own 300 g.
            assertThat(state.factor).isWithin(1e-6).of(280.9 / 300.0)
        }
    }

    @Test
    fun `picking a unit with no known data opens the density prompt, and answering it retries`() = runTest {
        val ingredients = FakeIngredientRepository()
        val vm = viewModel(ingredients = ingredients)
        vm.uiState.test {
            advanceUntilIdle()
            vm.onModeChange(CookMode.INGREDIENT)
            vm.onIngredientSelected("line-uova")
            vm.onIngredientUnitChange(MeasureUnit.GRAM)
            advanceUntilIdle()

            val prompted = expectMostRecentItem()
            val prompt = prompted.pendingDensityPrompt
            assertThat(prompt).isNotNull()
            assertThat(prompt!!.ingredientId).isEqualTo("ing-uova")
            assertThat(prompt.requirement).isEqualTo(DensityRequirement.ITEM_WEIGHT)

            vm.onDensityPromptConfirm(null, 55.0)
            vm.onIngredientQuantityChange("110")
            advanceUntilIdle()

            val resolved = expectMostRecentItem()
            assertThat(resolved.pendingDensityPrompt).isNull()
            assertThat(ingredients.densityUpdates).containsExactly(Triple("ing-uova", null, 55.0))
            // 110 g / 55 g per egg = 2 eggs, exactly what the recipe already wants: factor stays 1.
            assertThat(resolved.factor).isWithin(1e-9).of(1.0)
        }
    }

    @Test
    fun `constraining an approximate ingredient explains itself`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onModeChange(CookMode.INGREDIENT)
            vm.onIngredientSelected("line-sale")
            vm.onIngredientQuantityChange("2")
            advanceUntilIdle()

            assertThat(expectMostRecentItem().error).isEqualTo(CookError.APPROXIMATE_INGREDIENT)
        }
    }

    @Test
    fun `a zero factor is refused rather than emptying the recipe`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onModeChange(CookMode.FACTOR)
            vm.onFactorChange("0")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.error).isEqualTo(CookError.NON_POSITIVE)
            assertThat(lineOf(state, "Farina").scaledText).isEqualTo("300 g")
        }
    }

    @Test
    fun `the suggested label follows the mode`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onServingsChange(6)
            advanceUntilIdle()
            assertThat(expectMostRecentItem().suggestedLabel)
                .isEqualTo(SuggestedLabel.Servings(6.0))

            vm.onModeChange(CookMode.PANTRY)
            vm.onPantryAmountChange("line-farina", "600")
            advanceUntilIdle()
            assertThat(expectMostRecentItem().suggestedLabel).isEqualTo(SuggestedLabel.Pantry)
        }
    }

    @Test
    fun `the original quantities stay visible next to the new ones`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onServingsChange(6)
            advanceUntilIdle()

            val flour = lineOf(expectMostRecentItem(), "Farina")
            assertThat(flour.originalText).isEqualTo("300 g")
            assertThat(flour.scaledText).isEqualTo("450 g")
        }
    }

    @Test
    fun `a quantity too small to measure is flagged`() = runTest {
        val tiny = CookTestData.cake.copy(
            id = "r-tiny",
            ingredients = listOf(CookTestData.line("Lievito", 4.0, MeasureUnit.GRAM, 0)),
        )
        val vm = viewModel(recipe = tiny, recipes = FakeRecipeRepository(listOf(tiny)))
        vm.uiState.test {
            advanceUntilIdle()
            vm.onModeChange(CookMode.FACTOR)
            vm.onFactorChange("0,1")
            advanceUntilIdle()

            assertThat(lineOf(expectMostRecentItem(), "Lievito").hasWarning).isTrue()
        }
    }

    @Test
    fun `adding to the shopping list sends the scaled lines, not the originals`() = runTest {
        val shopping = FakeShoppingRepository()
        val model = viewModel(shopping = shopping)
        advanceUntilIdle()

        model.onModeChange(CookMode.FACTOR)
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
}
