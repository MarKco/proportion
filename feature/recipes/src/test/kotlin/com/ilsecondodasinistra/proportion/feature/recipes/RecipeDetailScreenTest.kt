package com.ilsecondodasinistra.proportion.feature.recipes

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionTheme
import com.ilsecondodasinistra.proportion.feature.recipes.detail.DetailLine
import com.ilsecondodasinistra.proportion.feature.recipes.detail.RecipeDetailScreen
import com.ilsecondodasinistra.proportion.feature.recipes.detail.RecipeDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val content = RecipeDetailUiState.Content(
        recipe = TestData.cake,
        lines = listOf(DetailLine(id = "l-1", name = "Farina", quantityText = "300 g", note = null)),
        variants = emptyList(),
    )

    private var editedId: String? = null

    private fun render(state: RecipeDetailUiState) {
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) {
                RecipeDetailScreen(
                    state = state,
                    onBack = {},
                    onEdit = { editedId = it },
                    onCook = {},
                    onFavouriteToggle = {},
                    onDelete = {},
                )
            }
        }
    }

    @Test
    fun `the overflow button has a real content description`() {
        render(content)

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.recipe_detail_more_actions))
            .assertIsDisplayed()
    }

    @Test
    fun `a top-level edit button is always visible and reports the recipe id, no overflow menu needed`() {
        render(content)

        composeTestRule.onNodeWithTag("detail_edit").assertIsDisplayed().performClick()

        assertThat(editedId).isEqualTo(content.recipe.id)
    }
}
