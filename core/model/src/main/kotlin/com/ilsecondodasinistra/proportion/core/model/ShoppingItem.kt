package com.ilsecondodasinistra.proportion.core.model

/**
 * One line of the single persistent shopping list. Lines coming from different recipes merge when
 * their units share a category, and stay separate when they do not.
 */
data class ShoppingItem(
    val id: String,
    val ingredient: Ingredient,
    val quantity: Double?,
    val unit: MeasureUnit,
    val isChecked: Boolean = false,
    val sourceRecipeIds: List<String> = emptyList(),
)
