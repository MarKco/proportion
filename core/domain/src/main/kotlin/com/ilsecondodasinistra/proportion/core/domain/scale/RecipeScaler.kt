package com.ilsecondodasinistra.proportion.core.domain.scale

import com.ilsecondodasinistra.proportion.core.model.Recipe

fun interface RecipeScaler {
    fun scale(recipe: Recipe, constraint: ScaleConstraint): ScaleResult
}
