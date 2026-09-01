package com.ilsecondodasinistra.proportion.feature.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionTheme
import com.ilsecondodasinistra.proportion.feature.recipes.list.RecipeListScreen
import com.ilsecondodasinistra.proportion.feature.recipes.list.RecipeListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var typedQuery: String? = null
    private var clickedRecipeId: String? = null
    private var addRequested = false
    private var filtersCleared = false

    private fun render(state: RecipeListUiState) {
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) {
                RecipeListScreen(
                    state = state,
                    onQueryChange = { typedQuery = it },
                    onTagToggle = {},
                    onIngredientToggle = {},
                    onSortChange = {},
                    onClearFilters = { filtersCleared = true },
                    onRecipeClick = { clickedRecipeId = it },
                    onAddRecipe = { addRequested = true },
                )
            }
        }
    }

    private val populated = RecipeListUiState(
        recipes = listOf(TestData.cake, TestData.risotto),
        availableTags = listOf(TestData.dessertTag, TestData.firstCourseTag),
        availableIngredients = listOf(TestData.flour, TestData.eggs, TestData.rice),
        isLoading = false,
    )

    @Test
    fun `the list shows a card per recipe`() {
        render(populated)

        composeTestRule.onNodeWithTag("recipe_card_r-cake").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recipe_card_r-risotto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Torta di mele").assertIsDisplayed()
    }

    @Test
    fun `typing in the search field reports the query`() {
        render(populated)

        composeTestRule.onNodeWithTag("search_field").performTextInput("torta")

        assertThat(typedQuery).isEqualTo("torta")
    }

    @Test
    fun `tapping a card opens that recipe`() {
        render(populated)

        composeTestRule.onNodeWithTag("recipe_card_r-risotto").performClick()

        assertThat(clickedRecipeId).isEqualTo("r-risotto")
    }

    @Test
    fun `an empty library invites adding the first recipe`() {
        render(RecipeListUiState(isLoading = false, libraryIsEmpty = true))

        composeTestRule.onNodeWithTag("empty_library_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aggiungi una ricetta").performClick()

        assertThat(addRequested).isTrue()
    }

    @Test
    fun `no results offers to clear the filters`() {
        render(
            populated.copy(
                recipes = emptyList(),
                query = "zafferano",
                libraryIsEmpty = false,
            ),
        )

        composeTestRule.onNodeWithTag("no_results_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("Azzera i filtri").performClick()

        assertThat(filtersCleared).isTrue()
    }

    @Test
    fun `the result count is always visible`() {
        render(populated)

        composeTestRule.onNodeWithTag("result_count").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 ricette").assertIsDisplayed()
    }

    @Test
    fun `the add button is reachable from the list`() {
        render(populated)

        composeTestRule.onNodeWithTag("add_recipe_fab").performClick()

        assertThat(addRequested).isTrue()
    }
}
