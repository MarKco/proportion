package com.ilsecondodasinistra.proportion.core.model

/**
 * A recipe as the user entered it. Scalings are never written back here: they are saved as
 * [ScaleVariant]s, so the original always survives.
 *
 * @param servings null for recipes that are not expressed per person (a jam, a dough).
 */
data class Recipe(
    val id: String,
    val title: String,
    val servings: Int?,
    val steps: List<String>,
    val ingredients: List<RecipeIngredient>,
    val tags: List<Tag>,
    val notes: String? = null,
    val isFavourite: Boolean = false,
    val cookCount: Int = 0,
    val lastCookedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Soft-delete tombstone, set instead of removing the row — see phase 10 (folder sync). */
    val deletedAt: Long? = null,
)
