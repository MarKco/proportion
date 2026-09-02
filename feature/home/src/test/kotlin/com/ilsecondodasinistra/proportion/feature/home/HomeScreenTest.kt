package com.ilsecondodasinistra.proportion.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
