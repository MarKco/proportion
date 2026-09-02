package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.ilsecondodasinistra.proportion.core.model.Recipe
import kotlin.random.Random

/**
 * "What shall I cook?" — a random pick, optionally inside one tag.
 *
 * [excluding] is the recipe currently on screen: reshuffling that lands on the same card reads as a
 * broken button, so it is dropped from the candidates unless it is the only one.
 */
class RecipePicker {

    fun pick(
        recipes: List<Recipe>,
        tagId: String?,
        excluding: String?,
        random: Random,
    ): Recipe? {
        val matching = when (tagId) {
            null -> recipes
            else -> recipes.filter { recipe -> recipe.tags.any { it.id == tagId } }
        }
        if (matching.isEmpty()) return null

        val candidates = matching.filterNot { it.id == excluding }.ifEmpty { matching }
        return candidates[random.nextInt(candidates.size)]
    }
}
