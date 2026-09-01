package com.ilsecondodasinistra.proportion.feature.recipes.detail

import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant

/** One ingredient line, already formatted: the screen never does arithmetic. */
data class DetailLine(
    val id: String,
    val name: String,
    val quantityText: String,
    val note: String?,
)

sealed interface RecipeDetailUiState {
    data object Loading : RecipeDetailUiState

    /** The recipe was deleted, or the id no longer resolves after a restore. */
    data object NotFound : RecipeDetailUiState

    data class Content(
        val recipe: Recipe,
        val lines: List<DetailLine>,
        val variants: List<ScaleVariant>,
    ) : RecipeDetailUiState
}
