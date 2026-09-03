package com.ilsecondodasinistra.proportion.feature.editor

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val baseState = EditorUiState()

    private fun render(state: EditorUiState) {
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) {
                EditorScreen(
                    state = state,
                    onTitleChange = {},
                    onServingsChange = {},
                    onLineNameChange = { _, _ -> },
                    onSuggestionPick = { _, _ -> },
                    onLineQuantityChange = { _, _ -> },
                    onLineUnitChange = { _, _ -> },
                    onAddLine = {},
                    onNewLineFocusHandled = {},
                    onRemoveLine = {},
                    onStepChange = { _, _ -> },
                    onAddStep = {},
                    onRemoveStep = {},
                    onTagToggle = {},
                    onCreateTag = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun `the servings stepper buttons have real content descriptions`() {
        render(baseState)

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.editor_decrease_servings))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.editor_increase_servings))
            .assertIsDisplayed()
    }
}
