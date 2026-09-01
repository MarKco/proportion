package com.ilsecondodasinistra.proportion.core.domain.scale

import com.ilsecondodasinistra.proportion.core.model.Recipe
import kotlin.math.sqrt

/**
 * Baking does not scale in proportion: doubling a cake does not double its time in the oven, and
 * the tin has to change too. When a recipe tagged for the oven is pushed well outside its original
 * size, this raises a non-blocking caution.
 *
 * The tin suggestion keeps the batter depth constant, so the diameter grows with the square root of
 * the factor: a 24 cm tin at x1.5 becomes roughly 29 cm.
 */
class BakingAdvisor {

    fun advise(recipe: Recipe, factor: Double): ScaleWarning.BakingTimeCaution? {
        val isOvenRecipe = recipe.tags.any { it.isBuiltIn && it.key == OVEN_TAG_KEY }
        if (!isOvenRecipe) return null
        if (factor in SAFE_LOW..SAFE_HIGH) return null

        return ScaleWarning.BakingTimeCaution(
            factor = factor,
            suggestedTinDiameterRatio = sqrt(factor),
        )
    }

    companion object {
        /** The tag that marks a recipe as baked. Seeded with the other built-in tags. */
        const val OVEN_TAG_KEY = "oven"

        /** Inside this band the change is small enough that the usual timing still works. */
        const val SAFE_LOW = 0.7
        const val SAFE_HIGH = 1.4
    }
}
