package com.ilsecondodasinistra.proportion.feature.editor

import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeFilter
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

object EditorTestData {

    val dessertTag = Tag(id = "tag-dessert", key = "dessert", name = null, isBuiltIn = true)
    val ovenTag = Tag(id = "tag-oven", key = "oven", name = null, isBuiltIn = true)

    val flour = Ingredient("ing-farina", "Farina 00", "farina 00", MeasureUnit.GRAM)
    val eggs = Ingredient("ing-uova", "Uova", "uova", MeasureUnit.EGG)

    val cake = Recipe(
        id = "r-cake",
        title = "Torta di mele",
        servings = 4,
        steps = listOf("Sbatti le uova.", "Inforna."),
        ingredients = listOf(
            RecipeIngredient("l-1", flour, 0, 300.0, MeasureUnit.GRAM),
            RecipeIngredient("l-2", eggs, 1, 2.0, MeasureUnit.EGG),
        ),
        tags = listOf(dessertTag),
    )
}

class FakeRecipeRepository(initial: List<Recipe> = emptyList()) : RecipeRepository {

    private val stored = MutableStateFlow(initial)
    val saved = mutableListOf<Recipe>()

    override fun observeRecipes(filter: RecipeFilter): Flow<List<Recipe>> = stored

    override fun observeRecipe(id: String): Flow<Recipe?> =
        stored.map { list -> list.firstOrNull { it.id == id } }

    override fun observeRecipeCount(): Flow<Int> = stored.map { it.size }

    override suspend fun upsert(recipe: Recipe): String {
        saved += recipe
        stored.value = stored.value.filterNot { it.id == recipe.id } + recipe
        return recipe.id
    }

    override suspend fun delete(id: String) {
        stored.value = stored.value.filterNot { it.id == id }
    }

    override suspend fun markCooked(id: String, at: Long) = Unit

    override suspend fun setFavourite(id: String, favourite: Boolean) = Unit
}

class FakeIngredientRepository(initial: List<Ingredient> = emptyList()) : IngredientRepository {

    private val stored = MutableStateFlow(initial)
    val created = mutableListOf<Ingredient>()

    override fun observeAll(): Flow<List<Ingredient>> = stored

    override fun observeInUse(): Flow<List<Ingredient>> = stored

    override suspend fun findOrCreate(name: String, defaultUnit: MeasureUnit): Ingredient {
        val normalised = name.trim().lowercase()
        stored.value.firstOrNull { it.normalisedName == normalised }?.let { return it }

        val ingredient = Ingredient(
            id = "ing-$normalised",
            name = name.trim(),
            normalisedName = normalised,
            defaultUnit = defaultUnit,
        )
        created += ingredient
        stored.value = stored.value + ingredient
        return ingredient
    }
}

class FakeTagRepository(initial: List<Tag> = emptyList()) : TagRepository {

    private val stored = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Tag>> = stored

    override suspend fun findOrCreateUserTag(name: String): Tag {
        stored.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return it }

        val created = Tag(
            id = "tag-${name.trim().lowercase()}",
            key = null,
            name = name.trim(),
            isBuiltIn = false,
        )
        stored.value = stored.value + created
        return created
    }

    override suspend fun deleteUserTag(id: String) {
        stored.value = stored.value.filterNot { it.id == id }
    }
}
