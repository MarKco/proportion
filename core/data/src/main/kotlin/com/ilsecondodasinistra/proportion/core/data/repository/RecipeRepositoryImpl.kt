package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.data.toDomain
import com.ilsecondodasinistra.proportion.core.data.toEntity
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.RecipeDao
import com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer
import com.ilsecondodasinistra.proportion.core.domain.IngredientNames
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeFilter
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.model.Recipe
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class RecipeRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val namer: BuiltInIngredientNamer,
    private val time: TimeProvider,
) : RecipeRepository {

    override fun observeRecipes(filter: RecipeFilter): Flow<List<Recipe>> = flow {
        val query = filter.query.lowercase().trim()

        // ingredients.normalised_name is frozen to a built-in row's raw English key (see
        // ProPortionDatabase's seeding), so the SQL LIKE below can never match a built-in
        // ingredient by its resolved, current-language name — resolve that match here instead.
        val matchingBuiltInIds = if (query.isEmpty()) {
            emptyList()
        } else {
            val normalisedQuery = IngredientNames.normalise(filter.query)
            ingredientDao.observeAll().first()
                .filter { it.isBuiltIn }
                .filter { IngredientNames.normalise(namer.name(it.key!!)).contains(normalisedQuery) }
                .map { it.id }
        }

        emitAll(
            recipeDao.filtered(
                query = query,
                tagIds = filter.tagIds,
                tagCount = filter.tagIds.size,
                ingredientIds = filter.ingredientIds,
                ingredientCount = filter.ingredientIds.size,
                sort = filter.sort.name,
                matchingBuiltInIds = matchingBuiltInIds,
            ).map { rows -> rows.map { it.toDomain(namer) } },
        )
    }

    override fun observeRecipe(id: String): Flow<Recipe?> =
        recipeDao.observeById(id).map { it?.toDomain(namer) }

    override fun observeRecipeCount(): Flow<Int> = recipeDao.observeRecipeCount()

    override suspend fun upsert(recipe: Recipe): String {
        val now = time.now()
        val stamped = recipe.copy(
            createdAt = if (recipe.createdAt == 0L) now else recipe.createdAt,
            updatedAt = now,
        )

        // An ingredient line can carry a user-created ingredient the catalogue has not seen yet.
        // Built-in rows are seeded once and never need rewriting - see EntityMappers.kt's read-time
        // resolution; writing them here would overwrite their placeholder name/normalisedName
        // columns with today's app-language text, which findOrCreate's built-in fallback depends on
        // staying frozen to the raw key (see IngredientRepositoryImpl).
        ingredientDao.upsertAll(
            stamped.ingredients.map { it.ingredient }.filterNot { it.isBuiltIn }.map { it.toEntity() },
        )

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
