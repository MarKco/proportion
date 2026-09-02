package com.ilsecondodasinistra.proportion.feature.shopping

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
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
class ShoppingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var checkedId: String? = null
    private var checkedValue: Boolean? = null
    private var clearAllConfirmed = false
    private var clearAllDismissed = false

    private val baseState = ShoppingUiState(
        isLoading = false,
        items = listOf(
            ShoppingRow(
                id = "item-flour",
                name = "Farina",
                amountText = "300 g",
                isChecked = false,
                sourceCount = 2,
            ),
            ShoppingRow(
                id = "item-salt",
                name = "Sale",
                amountText = "",
                isChecked = true,
                sourceCount = 1,
            ),
        ),
        checkedCount = 1,
    )

    private fun render(state: ShoppingUiState) {
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) {
                ShoppingScreen(
                    state = state,
                    onCheckedChange = { id, checked ->
                        checkedId = id
                        checkedValue = checked
                    },
                    onClearChecked = {},
                    onClearAllRequested = {},
                    onClearAllDismissed = { clearAllDismissed = true },
                    onClearAllConfirmed = { clearAllConfirmed = true },
                    onShare = {},
                )
            }
        }
    }

    @Test
    fun `an empty list shows the empty state instead of a column`() {
        render(ShoppingUiState(isLoading = false, items = emptyList()))

        composeTestRule.onNodeWithTag("shopping_empty_state").assertIsDisplayed()
    }

    @Test
    fun `toggling a checkbox reports which item it was`() {
        render(baseState)

        composeTestRule.onNodeWithTag("shopping_row_item-flour").performClick()

        assertThat(checkedId).isEqualTo("item-flour")
        assertThat(checkedValue).isTrue()
    }

    @Test
    fun `the clear-all dialog appears and reports confirmation`() {
        render(baseState.copy(confirmClearAll = true))

        composeTestRule.onNodeWithTag("clear_all_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_all_confirm").performClick()

        assertThat(clearAllConfirmed).isTrue()
    }

    @Test
    fun `the clear-all dialog can be cancelled without clearing anything`() {
        render(baseState.copy(confirmClearAll = true))

        composeTestRule.onNodeWithTag("clear_all_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_all_cancel").performClick()

        assertThat(clearAllDismissed).isTrue()
        assertThat(clearAllConfirmed).isFalse()
    }

    @Test
    fun `the overflow button has a real content description`() {
        render(baseState)

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.shopping_more_actions))
            .assertIsDisplayed()
    }
}
