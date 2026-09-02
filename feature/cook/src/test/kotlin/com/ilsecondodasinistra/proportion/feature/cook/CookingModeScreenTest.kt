package com.ilsecondodasinistra.proportion.feature.cook

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CookingModeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var backPressed = false
    private var checkedIndex: Int? = null
    private var checkedValue: Boolean? = null
    private var ingredientsShown: Boolean? = null

    private val baseState = CookingModeUiState(
        isLoading = false,
        title = "Torta di mele",
        factor = 1.5,
        servingsText = "6",
        steps = listOf(
            CookingStep(0, "Sbatti le uova.", isDone = false),
            CookingStep(1, "Inforna a 180 gradi.", isDone = true),
        ),
        ingredients = listOf(
            CookingIngredient("Farina", "450 g"),
            CookingIngredient("Uova", "3 uova"),
        ),
    )

    private fun render(state: CookingModeUiState) {
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) {
                CookingModeScreen(
                    state = state,
                    onBack = { backPressed = true },
                    onStepChecked = { index, done ->
                        checkedIndex = index
                        checkedValue = done
                    },
                    onToggleIngredients = { ingredientsShown = it },
                )
            }
        }
    }

    @Test
    fun `the steps render at their index with their checked state`() {
        render(baseState)

        composeTestRule.onNodeWithTag("cooking_step_0").assertIsDisplayed().assertIsOff()
        composeTestRule.onNodeWithTag("cooking_step_1").assertIsDisplayed().assertIsOn()
    }

    @Test
    fun `the progress reads the number of steps done out of the total`() {
        render(baseState)

        composeTestRule.onNodeWithTag("cooking_mode_progress").assertTextEquals("1 / 2")
    }

    @Test
    fun `checking a step reports its index and the new state`() {
        render(baseState)

        composeTestRule.onNodeWithTag("cooking_step_0").performClick()

        assertThat(checkedIndex).isEqualTo(0)
        assertThat(checkedValue).isTrue()
    }

    @Test
    fun `unchecking a done step reports false`() {
        render(baseState)

        composeTestRule.onNodeWithTag("cooking_step_1").performClick()

        assertThat(checkedIndex).isEqualTo(1)
        assertThat(checkedValue).isFalse()
    }

    @Test
    fun `the close action reports back`() {
        render(baseState)

        composeTestRule.onNodeWithTag("cooking_mode_close").performClick()

        assertThat(backPressed).isTrue()
    }

    @Test
    fun `the ingredients button opens the sheet`() {
        render(baseState)

        composeTestRule.onNodeWithTag("cooking_ingredients_button").performClick()

        assertThat(ingredientsShown).isTrue()
    }

    @Test
    fun `the ingredients button has a real content description`() {
        // On-device TalkBack testing found this button's visible text label was not exposed to
        // accessibility, so the icon now carries an explicit description too.
        render(baseState)

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.cooking_mode_ingredients))
            .assertIsDisplayed()
    }

    @Test
    fun `a recipe without steps shows the no-steps message instead of an empty screen`() {
        render(baseState.copy(steps = emptyList()))

        composeTestRule.onNodeWithTag("cooking_mode_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cooking_mode_no_steps").assertIsDisplayed()
    }
}
