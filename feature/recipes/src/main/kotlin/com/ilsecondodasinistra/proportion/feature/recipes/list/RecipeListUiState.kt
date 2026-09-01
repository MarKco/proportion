package com.ilsecondodasinistra.proportion.feature.recipes.list

import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeSort
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.Tag

data class RecipeListUiState(
    val query: String = "",
    val selectedTagIds: Set<String> = emptySet(),
    val selectedIngredientIds: Set<String> = emptySet(),
    val sort: RecipeSort = RecipeSort.RECENT,
    val recipes: List<Recipe> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val availableIngredients: List<Ingredient> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * True when there are no recipes at all, as opposed to none matching the current filters. The
     * two need different empty states: one invites writing a first recipe, the other offers to
     * clear the filters.
     */
    val libraryIsEmpty: Boolean = false,
) {
    val resultCount: Int get() = recipes.size

    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || selectedTagIds.isNotEmpty() || selectedIngredientIds.isNotEmpty()
}
