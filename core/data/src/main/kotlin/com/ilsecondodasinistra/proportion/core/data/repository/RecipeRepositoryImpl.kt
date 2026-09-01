package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.data.toDomain
import com.ilsecondodasinistra.proportion.core.data.toEntity
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.RecipeDao
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeFilter
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.model.Recipe
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val time: TimeProvider,
) : RecipeRepository {

    override fun observeRecipes(filter: RecipeFilter): Flow<List<Recipe>> =
        recipeDao.filtered(
            query = filter.query.lowercase().trim(),
            tagIds = filter.tagIds,
            tagCount = filter.tagIds.size,
            ingredientIds = filter.ingredientIds,
            ingredientCount = filter.ingredientIds.size,
            sort = filter.sort.name,
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecipe(id: String): Flow<Recipe?> =
        recipeDao.observeById(id).map { it?.toDomain() }

    override fun observeRecipeCount(): Flow<Int> = recipeDao.observeRecipeCount()

    override suspend fun upsert(recipe: Recipe): String {
        val now = time.now()
        val stamped = recipe.copy(
            createdAt = if (recipe.createdAt == 0L) now else recipe.createdAt,
            updatedAt = now,
        )

        // An ingredient line can carry an ingredient the catalogue has not seen yet.
        ingredientDao.upsertAll(stamped.ingredients.map { it.ingredient.toEntity() })

        recipeDao.upsertRecipe(
            recipe = stamped.toEntity(),
            lines = stamped.ingredients.mapIndexed { index, line ->
                line.copy(position = index).toEntity(stamped.id)
            },
            tagIds = stamped.tags.map { it.id },
        )
        return stamped.id
    }

    override suspend fun delete(id: String) = recipeDao.deleteRecipe(id)

    override suspend fun markCooked(id: String, at: Long) = recipeDao.markCooked(id, at)

    override suspend fun setFavourite(id: String, favourite: Boolean) =
        recipeDao.setFavourite(id, favourite, time.now())
}
