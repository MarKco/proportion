package com.ilsecondodasinistra.proportion.feature.editor

import com.ilsecondodasinistra.proportion.core.domain.unit.DensityRequirement
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Tag

/**
 * One editable ingredient row. Quantity stays text until save, so typing "1," is not an error.
 *
 * [unit] is null on a fresh line: pinning a default in advance turns "type 300, then pick cups"
 * into a conversion of a number that was never in grams. [isUnitChosen] is the same idea one step
 * further — a unit the catalogue suggested (see [EditorViewModel.onSuggestionPick]) is a hint, not
 * the user's answer, so the first deliberate pick still takes the quantity at face value.
 */
data class EditorLine(
    val id: String,
    val name: String = "",
    val quantity: String = "",
    val unit: MeasureUnit? = null,
    val isUnitChosen: Boolean = false,
    val note: String? = null,
)

/**
 * A unit change on [lineIndex] would cross a category boundary that needs data [ingredientId]
 * doesn't have yet — surfaced as a "density unknown" prompt. [qty]/[fromUnit]/[toUnit] are the
 * conversion the prompt's answer will retry.
 */
data class DensityPromptRequest(
    val lineIndex: Int,
    val ingredientId: String,
    val ingredientName: String,
    val requirement: DensityRequirement,
    val qty: Double,
    val fromUnit: MeasureUnit,
    val toUnit: MeasureUnit,
)

enum class ValidationError {
    TITLE_REQUIRED,
    INGREDIENTS_REQUIRED,
    QUANTITY_REQUIRED,
    UNIT_REQUIRED,
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
    /** Set when a unit change needs density/item-weight data the ingredient doesn't have yet. */
    val pendingDensityPrompt: DensityPromptRequest? = null,
) {
    val isEditing: Boolean get() = recipeId != null

    companion object {
        const val DEFAULT_SERVINGS = 4
    }
}
