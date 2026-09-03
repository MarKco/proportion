package com.ilsecondodasinistra.proportion.feature.editor

import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Tag

/** One editable ingredient row. Quantity stays text until save, so typing "1," is not an error. */
data class EditorLine(
    val id: String,
    val name: String = "",
    val quantity: String = "",
    val unit: MeasureUnit = MeasureUnit.GRAM,
    val note: String? = null,
)

enum class ValidationError {
    TITLE_REQUIRED,
    INGREDIENTS_REQUIRED,
    QUANTITY_REQUIRED,
}

data class EditorUiState(
    val recipeId: String? = null,
    val title: String = "",
    val servings: Int? = DEFAULT_SERVINGS,
    val notes: String = "",
    val lines: List<EditorLine> = emptyList(),
    val steps: List<String> = listOf(""),
    val selectedTagIds: Set<String> = emptySet(),
    val availableTags: List<Tag> = emptyList(),
    /** Catalogue matches for the line currently being typed into. */
    val suggestions: List<Ingredient> = emptyList(),
    val suggestionLineIndex: Int? = null,
    val errors: Set<ValidationError> = emptySet(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /**
     * Set only by [EditorViewModel.onAddLine] to the line it just appended, so the screen can
     * focus and scroll to that one row without guessing from a line-count change — which would
     * also (wrongly) fire the moment an existing recipe's saved lines finish loading. Cleared by
     * the screen once it has acted on it.
     */
    val justAddedLineId: String? = null,
) {
    val isEditing: Boolean get() = recipeId != null

    companion object {
        const val DEFAULT_SERVINGS = 4
    }
}
