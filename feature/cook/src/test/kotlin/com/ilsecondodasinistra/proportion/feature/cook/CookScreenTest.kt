package com.ilsecondodasinistra.proportion.feature.cook

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionTheme
import com.ilsecondodasinistra.proportion.core.domain.scale.SnapOption
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CookScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var servingsAsked: Int? = null
    private var acceptedSnap: SnapOption? = null
    private var cardShown: Boolean? = null
    private var addedToShoppingList = false
    private var cookingModeStarted = false
    private var savedVariant: Pair<String, Boolean>? = null

    private val baseState = CookUiState(
        isLoading = false,
        recipe = CookTestData.cake,
        mode = CookMode.SERVINGS,
        servingsInput = 6,
        factor = 1.5,
        servings = 6.0,
        lines = listOf(
            CookLine(
                lineId = "line-farina",
                name = "Farina",
                originalText = "300 g",
                scaledText = "450 g",
                unit = MeasureUnit.GRAM,
                isScaled = true,
            ),
            CookLine(
                lineId = "line-uova",
                name = "Uova",
                originalText = "2 uova",
                scaledText = "3 uova",
                unit = MeasureUnit.EGG,
                isScaled = true,
            ),
        ),
    )

    private fun render(state: CookUiState) {
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) {
                CookScreen(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onModeChange = {},
                    onServingsChange = { servingsAsked = it },
                    onFactorChange = {},
                    onIngredientSelected = {},
                    onIngredientQuantityChange = {},
                    onPantryAmountChange = { _, _ -> },
                    onSnapAccept = { acceptedSnap = it },
                    onShowCard = { cardShown = it },
                    onSaveRequested = {},
                    onSaveVariant = { label, asDefault -> savedVariant = label to asDefault },
                    onAddToShoppingList = { addedToShoppingList = true },
                    onStartCooking = { cookingModeStarted = true },
                )
            }
        }
    }

    @Test
    fun `the four modes are offered`() {
        render(baseState)

        // The row scrolls, so the last chip exists without necessarily being on screen.
        CookMode.entries.forEach { mode ->
            composeTestRule.onNodeWithTag("mode_${mode.name}").assertExists()
        }
        composeTestRule.onNodeWithTag("mode_${CookMode.SERVINGS.name}").assertIsDisplayed()
    }

    @Test
    fun `each line shows the new quantity next to the original`() {
        render(baseState)

        composeTestRule.onNodeWithTag("scaled_line-farina").assertIsDisplayed()
        composeTestRule.onNodeWithText("450 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("300 g").assertIsDisplayed()
    }

    @Test
    fun `the stepper reports the new serving count`() {
        render(baseState)

        composeTestRule.onNodeWithTag("servings_plus").performClick()

        assertThat(servingsAsked).isEqualTo(7)
    }

    @Test
    fun `the servings stepper buttons have real content descriptions`() {
        render(baseState)

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.cook_decrease_servings))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.cook_increase_servings))
            .assertIsDisplayed()
    }

    @Test
    fun `an impractical amount shows a warning with its snap`() {
        render(
            baseState.copy(
                lines = baseState.lines.map { line ->
                    if (line.lineId != "line-uova") {
                        line
                    } else {
                        line.copy(
                            scaledText = "4 ½ uova",
                            hasWarning = true,
                            warningText = "4 ½ uova",
                            snaps = listOf(
                                SnapChip("4 uova", SnapOption("line-uova", 4.0, 1.333)),
                                SnapChip("5 uova", SnapOption("line-uova", 5.0, 1.667)),
                            ),
                        )
                    }
                },
            ),
        )

        composeTestRule.onNodeWithTag("warning_line-uova").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("snap_4").performScrollTo().performClick()

        assertThat(acceptedSnap?.targetQty).isEqualTo(4.0)
    }

    @Test
    fun `a warning appears and disappears without leaving stale text behind`() {
        val withWarning = baseState.copy(
            lines = baseState.lines.map { line ->
                if (line.lineId != "line-uova") {
                    line
                } else {
                    line.copy(hasWarning = true, warningText = "1 uovo")
                }
            },
        )

        // A single setContent that mutates state, rather than two `render()` calls: the test
        // rule refuses a second setContent on the same Activity, and this also better matches
        // how the real screen recomposes in place as the ViewModel emits new state.
        var state by mutableStateOf(withWarning)
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) {
                CookScreen(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onModeChange = {},
                    onServingsChange = {},
                    onFactorChange = {},
                    onIngredientSelected = {},
                    onIngredientQuantityChange = {},
                    onPantryAmountChange = { _, _ -> },
                    onSnapAccept = {},
                    onShowCard = {},
                    onSaveRequested = {},
                    onSaveVariant = { _, _ -> },
                    onAddToShoppingList = {},
                    onStartCooking = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("warning_line-uova").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1 uovo non è una quantità pratica").performScrollTo().assertIsDisplayed()

        state = state.copy(lines = state.lines.map { it.copy(hasWarning = false, warningText = null) })

        composeTestRule.onNodeWithTag("warning_line-uova").assertDoesNotExist()
        composeTestRule.onNodeWithText("1 uovo non è una quantità pratica").assertDoesNotExist()
    }

    @Test
    fun `the oven advisory is shown above the list`() {
        render(baseState.copy(ovenAdvisory = OvenAdvisory(factor = 2.0, tinDiameterRatio = 1.414)))

        composeTestRule.onNodeWithTag("oven_advisory").assertIsDisplayed()
    }

    @Test
    fun `the shopping list can be filled from the adjust view`() {
        render(baseState)

        composeTestRule.onNodeWithTag("add_to_shopping_button").performScrollTo().performClick()

        assertThat(addedToShoppingList).isTrue()
    }

    @Test
    fun `the card can be opened from the adjust view`() {
        render(baseState)

        composeTestRule.onNodeWithTag("show_card_button").performScrollTo().performClick()

        assertThat(cardShown).isTrue()
    }

    @Test
    fun `cooking mode can be started from the card`() {
        render(baseState.copy(showCard = true))

        composeTestRule.onNodeWithTag("start_cooking_button").performScrollTo().performClick()

        assertThat(cookingModeStarted).isTrue()
    }

    @Test
    fun `the card shows the scaled quantities and the original steps`() {
        render(baseState.copy(showCard = true))

        composeTestRule.onNodeWithTag("scaled_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_line-farina").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("450 g").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sbatti le uova.").assertIsDisplayed()
    }

    @Test
    fun `the pantry mode reports the bottleneck`() {
        render(
            baseState.copy(
                mode = CookMode.PANTRY,
                bottleneckLineId = "line-farina",
                servings = 4.3,
            ),
        )

        composeTestRule.onNodeWithTag("achievable").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Collo di bottiglia").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an error explains itself instead of blanking the list`() {
        render(baseState.copy(error = CookError.NON_POSITIVE))

        composeTestRule.onNodeWithTag("cook_error").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("scaled_line-farina").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `saving a scaling without touching the checkbox does not mark it as default`() {
        render(baseState.copy(saveDialogVisible = true, suggestedLabel = SuggestedLabel.Servings(6.0)))

        composeTestRule.onNodeWithTag("variant_save_confirm").performClick()

        assertThat(savedVariant?.second).isFalse()
    }

    @Test
    fun `checking the default box saves the scaling as the recipe's default`() {
        render(baseState.copy(saveDialogVisible = true, suggestedLabel = SuggestedLabel.Servings(6.0)))

        composeTestRule.onNodeWithTag("variant_default_row").performClick()
        composeTestRule.onNodeWithTag("variant_save_confirm").performClick()

        assertThat(savedVariant?.second).isTrue()
    }
}
