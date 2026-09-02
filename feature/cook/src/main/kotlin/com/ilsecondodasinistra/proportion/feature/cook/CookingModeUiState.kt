package com.ilsecondodasinistra.proportion.feature.cook

/** One line of the method. Checked state lives only in memory: this screen is not meant to be left. */
data class CookingStep(val index: Int, val text: String, val isDone: Boolean)

/** An ingredient at the scale cooking mode was entered with, already formatted for display. */
data class CookingIngredient(val name: String, val amountText: String)

/**
 * A read-only run through the recipe at a fixed scale, decided once when cooking mode is entered.
 * There is no "adjust" here — that is [CookViewModel]'s job — so nothing here recomputes a factor.
 */
data class CookingModeUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val factor: Double = 1.0,
    val servingsText: String? = null,
    val steps: List<CookingStep> = emptyList(),
    val ingredients: List<CookingIngredient> = emptyList(),
    val showIngredients: Boolean = false,
) {
    val doneCount: Int get() = steps.count { it.isDone }
}
