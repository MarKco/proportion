package com.ilsecondodasinistra.proportion.feature.recipes

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.transfer.PlainTextStrings
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant
import com.ilsecondodasinistra.proportion.feature.recipes.detail.RecipeDetailUiState
import com.ilsecondodasinistra.proportion.feature.recipes.detail.RecipeDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecipeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recipes = FakeRecipeRepository(listOf(TestData.cake, TestData.risotto))
    private val variants = FakeScaleVariantRepository()

    /**
     * [recipe] and [variants] override the shared fakes above with fresh ones seeded exactly as
     * given, for tests that need a precise recipe state or hand-built variants; omitting them keeps
     * using (and can still mutate through) the shared [recipes] / [variants] fakes.
     */
    private fun viewModel(
        id: String = "r-cake",
        recipe: Recipe? = null,
        variants: List<ScaleVariant>? = null,
    ) = RecipeDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("recipeId" to id)),
        recipeRepository = recipe?.let { FakeRecipeRepository(listOf(it, TestData.risotto)) } ?: recipes,
        variantRepository = variants?.let { FakeScaleVariantRepository(it) } ?: this.variants,
        transferRepository = FakeTransferRepository(listOf(recipe ?: TestData.cake, TestData.risotto)),
        formatter = testFormatter(),
        scaler = testScaler(),
    )

    @Test
    fun `the recipe is shown with its quantities already formatted`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state).isInstanceOf(RecipeDetailUiState.Content::class.java)
            val content = state as RecipeDetailUiState.Content
            assertThat(content.recipe.title).isEqualTo("Torta di mele")
            assertThat(content.lines.map { it.quantityText })
                .containsExactly("300 g", "2 uova").inOrder()
        }
    }

    @Test
    fun `a recipe that does not exist reports not found`() = runTest {
        viewModel(id = "r-nope").uiState.test {
            advanceUntilIdle()

            assertThat(expectMostRecentItem()).isEqualTo(RecipeDetailUiState.NotFound)
        }
    }

    @Test
    fun `saved scalings are listed with the recipe`() = runTest {
        variants.save("r-cake", "Per 6", ScaleConstraint.ByServings(6.0), asDefault = true)

        viewModel().uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem() as RecipeDetailUiState.Content

            assertThat(content.variants.map { it.label }).containsExactly("Per 6")
        }
    }

    @Test
    fun `variants of other recipes are not shown`() = runTest {
        variants.save("r-risotto", "Per 4", ScaleConstraint.ByServings(4.0), asDefault = false)

        viewModel().uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem() as RecipeDetailUiState.Content

            assertThat(content.variants).isEmpty()
        }
    }

    @Test
    fun `a recipe with a default variant opens at that scaling`() = runTest {
        val defaultVariantForSix = testScaleVariant(
            id = "variant-six",
            recipeId = "r-cake",
            label = "Per 6",
            constraint = ScaleConstraint.ByServings(6.0),
            isDefault = true,
        )

        val model = viewModel(variants = listOf(defaultVariantForSix))
        model.uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem() as RecipeDetailUiState.Content

            assertThat(content.showingVariant?.label).isEqualTo("Per 6")
            assertThat(content.lines.first { it.name == "Farina" }.quantityText).isEqualTo("450 g")
        }
    }

    @Test
    fun `a recipe without a default variant opens as written`() = runTest {
        val model = viewModel(variants = emptyList())
        model.uiState.test {
            advanceUntilIdle()

            assertThat((expectMostRecentItem() as RecipeDetailUiState.Content).showingVariant).isNull()
        }
    }

    @Test
    fun `view original goes back to the recipe as entered`() = runTest {
        val defaultVariantForSix = testScaleVariant(
            id = "variant-six",
            recipeId = "r-cake",
            label = "Per 6",
            constraint = ScaleConstraint.ByServings(6.0),
            isDefault = true,
        )

        val model = viewModel(variants = listOf(defaultVariantForSix))
        model.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            model.onShowOriginal()
            advanceUntilIdle()
            val content = expectMostRecentItem() as RecipeDetailUiState.Content

            assertThat(content.showingVariant).isNull()
            assertThat(content.lines.first { it.name == "Farina" }.quantityText).isEqualTo("300 g")
        }
    }

    @Test
    fun `a variant whose constraint no longer applies falls back to the original instead of failing`() =
        runTest {
            // "l-deleted" does not match any line on TestData.cake, exactly as if the ingredient
            // line the variant was built on had since been removed from the recipe.
            val variantOnDeletedLine = testScaleVariant(
                id = "variant-deleted",
                recipeId = "r-cake",
                label = "Two eggs",
                constraint = ScaleConstraint.ByIngredient(
                    lineId = "l-deleted",
                    qty = 2.0,
                    unit = MeasureUnit.EGG,
                ),
                isDefault = true,
            )

            val model = viewModel(variants = listOf(variantOnDeletedLine))
            model.uiState.test {
                advanceUntilIdle()
                val content = expectMostRecentItem() as RecipeDetailUiState.Content

                assertThat(content.showingVariant).isNull()
                assertThat(content.lines).isNotEmpty()
            }
        }

    @Test
    fun `the detail reports how many times the recipe was cooked`() = runTest {
        val model = viewModel(recipe = TestData.cake.copy(cookCount = 4))
        model.uiState.test {
            advanceUntilIdle()

            assertThat((expectMostRecentItem() as RecipeDetailUiState.Content).cookCount).isEqualTo(4)
        }
    }

    @Test
    fun `toggling the favourite writes through to the repository`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            assertThat((expectMostRecentItem() as RecipeDetailUiState.Content).recipe.isFavourite).isFalse()

            vm.onFavouriteToggle()
            advanceUntilIdle()

            assertThat((expectMostRecentItem() as RecipeDetailUiState.Content).recipe.isFavourite).isTrue()
        }
    }

    @Test
    fun `deleting removes the recipe from the library`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onDelete()
            advanceUntilIdle()

            assertThat(expectMostRecentItem()).isEqualTo(RecipeDetailUiState.NotFound)
        }
    }

    @Test
    fun `sharing as text produces a readable recipe`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            var shared: String? = null
            vm.onShareText(italianPlainTextStrings()) { shared = it }

            assertThat(shared).contains("Torta di mele")
            assertThat(shared).contains("Per 4 persone")
            assertThat(shared).contains("300 g")
            assertThat(shared).contains("Condivisa con ProPortion")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sharing as a file produces a parseable proportion file`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            var shared: String? = null
            vm.onShareFile { shared = it }
            advanceUntilIdle()

            assertThat(shared).contains("\"format\": \"proportion\"")
            assertThat(shared).contains("Torta di mele")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an approximate ingredient keeps its wording instead of a number`() = runTest {
        val withSalt = TestData.cake.copy(
            id = "r-salt",
            ingredients = TestData.cake.ingredients + RecipeIngredient(
                id = "l-salt",
                ingredient = TestData.ingredient("Sale", MeasureUnit.TO_TASTE),
                position = 2,
                quantity = null,
                unit = MeasureUnit.TO_TASTE,
            ),
        )
        recipes.upsert(withSalt)

        viewModel(id = "r-salt").uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem() as RecipeDetailUiState.Content

            assertThat(content.lines.last().quantityText).isEqualTo("q.b.")
        }
    }
}

private fun italianPlainTextStrings() = PlainTextStrings(
    servings = { "Per $it persone" },
    scaledFor = { "Riproporzionata per $it persone" },
    notPerPerson = "Non a persona",
    ingredientsTitle = "Ingredienti",
    methodTitle = "Procedimento",
    attribution = "Condivisa con ProPortion",
)
