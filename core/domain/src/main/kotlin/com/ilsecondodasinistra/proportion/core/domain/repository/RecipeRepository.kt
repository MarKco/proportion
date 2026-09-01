package com.ilsecondodasinistra.proportion.core.domain.repository

import com.ilsecondodasinistra.proportion.core.model.Recipe
import kotlinx.coroutines.flow.Flow

enum class RecipeSort { RECENT, ALPHABETICAL, MOST_COOKED }

/**
 * The three filters combine with AND. A recipe matches the ingredient filter only when it contains
 * **every** selected ingredient, and the tag filter when it carries **any** of the selected tags.
 */
data class RecipeFilter(
    val query: String = "",
    val tagIds: List<String> = emptyList(),
    val ingredientIds: List<String> = emptyList(),
    val sort: RecipeSort = RecipeSort.RECENT,
)

interface RecipeRepository {
    fun observeRecipes(filter: RecipeFilter = RecipeFilter()): Flow<List<Recipe>>
    fun observeRecipe(id: String): Flow<Recipe?>
    fun observeRecipeCount(): Flow<Int>
    suspend fun upsert(recipe: Recipe): String
    suspend fun delete(id: String)
    suspend fun markCooked(id: String, at: Long)
    suspend fun setFavourite(id: String, favourite: Boolean)
}
