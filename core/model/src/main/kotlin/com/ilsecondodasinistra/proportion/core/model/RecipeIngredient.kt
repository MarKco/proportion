package com.ilsecondodasinistra.proportion.core.model

/**
 * One ingredient line of a recipe: this is where quantity and unit live.
 *
 * @param quantity null when [unit] is approximate ("q.b.").
 * @param displayText what the user typed, when it carries more than the number does ("½ bustina").
 */
data class RecipeIngredient(
    val id: String,
    val ingredient: Ingredient,
    val position: Int,
    val quantity: Double?,
    val unit: MeasureUnit,
    val displayText: String? = null,
    val note: String? = null,
)
