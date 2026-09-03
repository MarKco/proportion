package com.ilsecondodasinistra.proportion.feature.recipes.detail

import com.ilsecondodasinistra.proportion.core.domain.unit.DensityRequirement
import com.ilsecondodasinistra.proportion.core.domain.unit.FormattedQuantity
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant

/** One ingredient line, already formatted: the screen never does arithmetic. */
data class DetailLine(
    val id: String,
    val name: String,
    val quantityText: String,
    val note: String?,
)

/**
 * What tapping a [DetailLine] and picking another unit resolves to — informational only, never
 * written back to the recipe.
 */
sealed interface ConversionResult {
    data class Converted(val formatted: FormattedQuantity) : ConversionResult
    data class NeedsDensity(val prompt: DensityPromptRequest) : ConversionResult
    data object Unsupported : ConversionResult
}

/** Mirrors the editor's/cook's own prompt: asked once, then persisted on the ingredient. */
data class DensityPromptRequest(
    val ingredientId: String,
    val ingredientName: String,
    val requirement: DensityRequirement,
)

/** The saved scaling currently on screen, if any. Null means the recipe as written. */
data class ShowingVariant(
    val variantId: String,
    val label: String,
)

sealed interface RecipeDetailUiState {
    data object Loading : RecipeDetailUiState

    /** The recipe was deleted, or the id no longer resolves after a restore. */
    data object NotFound : RecipeDetailUiState

    data class Content(
        val recipe: Recipe,
        val lines: List<DetailLine>,
        val variants: List<ScaleVariant>,
        /**
         * The saved scaling currently applied, or null when showing the recipe as entered — either
         * because there is no default variant, the user asked for the original, or the default
         * variant's constraint no longer resolves against the recipe as it is now.
         */
        val showingVariant: ShowingVariant? = null,
        val cookCount: Int = recipe.cookCount,
    ) : RecipeDetailUiState
}
