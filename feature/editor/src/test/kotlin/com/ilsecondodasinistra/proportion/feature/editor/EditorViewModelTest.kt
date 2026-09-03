package com.ilsecondodasinistra.proportion.feature.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recipes = FakeRecipeRepository(listOf(EditorTestData.cake))
    private val ingredients = FakeIngredientRepository(
        listOf(EditorTestData.flour, EditorTestData.eggs),
    )
    private val tags = FakeTagRepository(listOf(EditorTestData.dessertTag, EditorTestData.ovenTag))

    private fun viewModel(recipeId: String? = null, ingredients: FakeIngredientRepository = this.ingredients) =
        EditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("recipeId" to recipeId)),
            recipeRepository = recipes,
            ingredientRepository = ingredients,
            tagRepository = tags,
            converter = DefaultUnitConverter(),
        )

    @Test
    fun `a new draft starts with one empty ingredient line`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.isEditing).isFalse()
            assertThat(state.lines).hasSize(1)
            assertThat(state.lines.single().name).isEmpty()
            assertThat(state.isDirty).isFalse()
        }
    }

    @Test
    fun `editing an existing recipe loads its lines in order`() = runTest {
        viewModel(recipeId = "r-cake").uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.isEditing).isTrue()
            assertThat(state.title).isEqualTo("Torta di mele")
            assertThat(state.servings).isEqualTo(4)
            assertThat(state.lines.map { it.name }).containsExactly("Farina 00", "Uova").inOrder()
            assertThat(state.lines.first().quantity).isEqualTo("300")
            assertThat(state.selectedTagIds).containsExactly("tag-dessert")
            assertThat(state.steps).containsExactly("Sbatti le uova.", "Inforna.").inOrder()
        }
    }

    @Test
    fun `loading an existing recipe's lines does not mark any of them as just added`() = runTest {
        // A regression guard: the editor used to infer "just added" from the line count going up,
        // which also (wrongly) fired the moment this load populates the draft from 1 placeholder
        // line to the recipe's real two - focusing and scrolling to the last one as if the user had
        // just tapped "Aggiungi ingrediente".
        viewModel(recipeId = "r-cake").uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.lines).hasSize(2)
            assertThat(state.justAddedLineId).isNull()
        }
    }

    @Test
    fun `adding a line marks only that line as just added, until the screen reports it handled`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onAddLine()
            advanceUntilIdle()
            val afterAdd = expectMostRecentItem()
            val newLineId = afterAdd.lines.last().id
            assertThat(afterAdd.justAddedLineId).isEqualTo(newLineId)

            vm.onNewLineFocusHandled()
            advanceUntilIdle()
            assertThat(expectMostRecentItem().justAddedLineId).isNull()
        }
    }

    @Test
    fun `the first edit marks the draft dirty`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Pasta")
            advanceUntilIdle()

            assertThat(expectMostRecentItem().isDirty).isTrue()
        }
    }

    @Test
    fun `saving without a title reports the error and writes nothing`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onLineNameChange(0, "Farina 00")
            vm.onLineQuantityChange(0, "300")
            vm.onSave()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().errors).contains(ValidationError.TITLE_REQUIRED)
        }
        assertThat(recipes.saved).isEmpty()
    }

    @Test
    fun `saving without an ingredient reports the error and writes nothing`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Pasta in bianco")
            vm.onSave()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().errors).contains(ValidationError.INGREDIENTS_REQUIRED)
        }
        assertThat(recipes.saved).isEmpty()
    }

    @Test
    fun `a quantity is required unless the unit is approximate`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Pasta")
            vm.onLineNameChange(0, "Sale")
            vm.onSave()
            advanceUntilIdle()
            assertThat(expectMostRecentItem().errors).contains(ValidationError.QUANTITY_REQUIRED)

            vm.onLineUnitChange(0, MeasureUnit.TO_TASTE)
            vm.onSave()
            advanceUntilIdle()
            assertThat(expectMostRecentItem().errors).isEmpty()
        }
        assertThat(recipes.saved).hasSize(1)
    }

    @Test
    fun `a saved recipe carries its lines, tags and steps`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Torta veloce")
            vm.onServingsChange(6)
            vm.onLineNameChange(0, "Farina 00")
            vm.onLineQuantityChange(0, "250")
            vm.onAddLine()
            vm.onLineNameChange(1, "Uova")
            vm.onLineQuantityChange(1, "3")
            vm.onLineUnitChange(1, MeasureUnit.EGG)
            vm.onTagToggle("tag-dessert")
            vm.onStepChange(0, "Mescola tutto.")
            vm.onSave()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val saved = recipes.saved.single()
        assertThat(saved.title).isEqualTo("Torta veloce")
        assertThat(saved.servings).isEqualTo(6)
        assertThat(saved.ingredients.map { it.ingredient.name })
            .containsExactly("Farina 00", "Uova").inOrder()
        assertThat(saved.ingredients.map { it.quantity }).containsExactly(250.0, 3.0).inOrder()
        assertThat(saved.ingredients.last().unit).isEqualTo(MeasureUnit.EGG)
        assertThat(saved.tags.map { it.id }).containsExactly("tag-dessert")
        assertThat(saved.steps).containsExactly("Mescola tutto.")
    }

    @Test
    fun `an existing ingredient is reused rather than duplicated`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Frittata")
            vm.onLineNameChange(0, "  uova  ")
            vm.onLineQuantityChange(0, "4")
            vm.onLineUnitChange(0, MeasureUnit.EGG)
            vm.onSave()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(ingredients.created).isEmpty()
        assertThat(recipes.saved.single().ingredients.single().ingredient.id).isEqualTo("ing-uova")
    }

    @Test
    fun `a new ingredient is created once`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Risotto")
            vm.onLineNameChange(0, "Riso Carnaroli")
            vm.onLineQuantityChange(0, "320")
            vm.onSave()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(ingredients.created.map { it.name }).containsExactly("Riso Carnaroli")
    }

    @Test
    fun `typing an ingredient name suggests matches from the catalogue`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onLineNameChange(0, "far")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.suggestions.map { it.name }).containsExactly("Farina 00")
            assertThat(state.suggestionLineIndex).isEqualTo(0)
        }
    }

    @Test
    fun `picking a suggestion applies its default unit when the line's unit is incompatible`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            vm.onSuggestionPick(0, EditorTestData.eggs)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().lines.single().unit).isEqualTo(MeasureUnit.EGG)
        }
    }

    @Test
    fun `picking a suggestion keeps an already-compatible unit instead of overwriting it`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            // KILOGRAM and flour's default (GRAM) are both mass units - the user's choice should win.
            vm.onLineUnitChange(0, MeasureUnit.KILOGRAM)
            vm.onSuggestionPick(0, EditorTestData.flour)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().lines.single().unit).isEqualTo(MeasureUnit.KILOGRAM)
        }
    }

    @Test
    fun `lines can be added, removed and reordered`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onLineNameChange(0, "Farina 00")
            vm.onAddLine()
            vm.onLineNameChange(1, "Uova")
            advanceUntilIdle()
            assertThat(expectMostRecentItem().lines.map { it.name })
                .containsExactly("Farina 00", "Uova").inOrder()

            vm.onMoveLine(1, 0)
            advanceUntilIdle()
            assertThat(expectMostRecentItem().lines.map { it.name })
                .containsExactly("Uova", "Farina 00").inOrder()

            vm.onRemoveLine(0)
            advanceUntilIdle()
            assertThat(expectMostRecentItem().lines.map { it.name }).containsExactly("Farina 00")
        }
    }

    @Test
    fun `a new user tag is created and selected`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onCreateTag("merenda")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.availableTags.map { it.name }).contains("merenda")
            assertThat(state.selectedTagIds).contains("tag-merenda")
        }
    }

    @Test
    fun `editing an existing recipe keeps its id`() = runTest {
        val vm = viewModel(recipeId = "r-cake")
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Torta di mele della nonna")
            vm.onSave()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val saved = recipes.saved.single()
        assertThat(saved.id).isEqualTo("r-cake")
        assertThat(saved.title).isEqualTo("Torta di mele della nonna")
    }

    @Test
    fun `saving reports completion so the screen can navigate back`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Pane")
            vm.onLineNameChange(0, "Farina 00")
            vm.onLineQuantityChange(0, "500")
            vm.onSave()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().isSaved).isTrue()
        }
    }

    @Test
    fun `a comma decimal separator is accepted`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onTitleChange("Sciroppo")
            vm.onLineNameChange(0, "Acqua")
            vm.onLineQuantityChange(0, "1,5")
            vm.onLineUnitChange(0, MeasureUnit.LITRE)
            vm.onSave()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(recipes.saved.single().ingredients.single().quantity).isEqualTo(1.5)
    }

    @Test
    fun `switching a line's unit across categories re-expresses the quantity via density`() = runTest {
        val flourWithDensity = EditorTestData.flour.copy(densityGramsPerMl = 0.53)
        val vm = viewModel(
            recipeId = "r-cake",
            ingredients = FakeIngredientRepository(listOf(flourWithDensity, EditorTestData.eggs)),
        )
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            vm.onLineUnitChange(0, MeasureUnit.MILLILITRE)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.pendingDensityPrompt).isNull()
            assertThat(state.lines[0].unit).isEqualTo(MeasureUnit.MILLILITRE)
            // 300 g / 0.53 g/ml = 566.03... ml.
            assertThat(state.lines[0].quantity.replace(',', '.').toDouble()).isWithin(0.01).of(300.0 / 0.53)
        }
    }

    @Test
    fun `switching to a unit with no known density asks for it, then retries once answered`() = runTest {
        val fakeIngredients = FakeIngredientRepository(listOf(EditorTestData.flour, EditorTestData.eggs))
        val vm = viewModel(recipeId = "r-cake", ingredients = fakeIngredients)
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            vm.onLineUnitChange(0, MeasureUnit.MILLILITRE)
            advanceUntilIdle()

            val prompted = expectMostRecentItem()
            val prompt = prompted.pendingDensityPrompt
            assertThat(prompt).isNotNull()
            assertThat(prompt!!.ingredientId).isEqualTo(EditorTestData.flour.id)
            // The unit switch itself still applies; only the quantity is left as it was typed.
            assertThat(prompted.lines[0].unit).isEqualTo(MeasureUnit.MILLILITRE)
            assertThat(prompted.lines[0].quantity).isEqualTo("300")

            vm.onDensityPromptConfirm(0.53, null)
            advanceUntilIdle()

            val resolved = expectMostRecentItem()
            assertThat(resolved.pendingDensityPrompt).isNull()
            assertThat(fakeIngredients.densityUpdates)
                .containsExactly(Triple(EditorTestData.flour.id, 0.53, null))
            assertThat(resolved.lines[0].quantity.replace(',', '.').toDouble()).isWithin(0.01).of(300.0 / 0.53)
        }
    }
}
