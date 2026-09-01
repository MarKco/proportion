package com.ilsecondodasinistra.proportion.core.model

/**
 * A scaling the user chose to keep.
 *
 * It stores the **constraint**, not the computed quantities, so it stays correct when the recipe is
 * edited afterwards. At most one variant per recipe has [isDefault] set; when it does, opening the
 * recipe shows that scaling with a way back to the original.
 */
data class ScaleVariant(
    val id: String,
    val recipeId: String,
    val label: String,
    val constraintPayload: String,
    val isDefault: Boolean = false,
    val createdAt: Long = 0L,
)
