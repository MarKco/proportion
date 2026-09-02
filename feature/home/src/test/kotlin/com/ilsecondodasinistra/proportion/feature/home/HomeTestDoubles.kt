package com.ilsecondodasinistra.proportion.feature.home

import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeFilter
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant
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

/**
 * A small library that exercises every card at once: one dessert, one first course, one
 * never-cooked recipe and one favourite. The dessert doubles as [lastCookedId] — the most recently
 * cooked recipe — so `continueCooking` has something concrete to point at.
 */
object HomeTestData {

    val dessertTag = Tag(id = "tag-dessert", key = "dessert", name = null, isBuiltIn = true, colorIndex = 0)
    val firstCourseTag = Tag(id = "tag-first", key = "first_course", name = null, isBuiltIn = true, colorIndex = 1)

    /** Carried by no recipe in [library]: exercises "a tag with no recipes says so". */
    val emptyTag = Tag(id = "tag-side", key = "side_dish", name = null, isBuiltIn = true, colorIndex = 2)

    val tags = listOf(dessertTag, firstCourseTag, emptyTag)

    val dessert = Recipe(
        id = "r-dessert",
        title = "Torta di mele",
        servings = 6,
        steps = listOf("Sbatti le uova."),
        ingredients = emptyList(),
        tags = listOf(dessertTag),
        cookCount = 3,
        lastCookedAt = 3_000L,
    )

    val firstCourse = Recipe(
        id = "r-first",
        title = "Risotto allo zafferano",
        servings = 2,
        steps = listOf("Tosta il riso."),
        ingredients = emptyList(),
        tags = listOf(firstCourseTag),
        cookCount = 1,
        lastCookedAt = 1_000L,
    )

    val neverCooked = Recipe(
        id = "r-never",
        title = "Marmellata di albicocche",
        servings = null,
        steps = listOf("Cuoci a lungo."),
        ingredients = emptyList(),
        tags = emptyList(),
    )

    val favourite = Recipe(
        id = "r-favourite",
        title = "Frittata di zucchine",
        servings = 2,
        steps = listOf("Sbatti e cuoci."),
        ingredients = emptyList(),
        tags = emptyList(),
        isFavourite = true,
        cookCount = 2,
        lastCookedAt = 2_000L,
    )

    val library = listOf(dessert, firstCourse, neverCooked, favourite)

    val lastCookedId = dessert.id
    val dessertTagId = dessertTag.id
    val dessertIds = listOf(dessert.id)
    val emptyTagId = emptyTag.id

    /** The label on the default scaling saved for [lastCookedId]. */
    const val LAST_COOKED_VARIANT_LABEL = "Per 6"
}

class FakeRecipeRepository(initial: List<Recipe> = emptyList()) : RecipeRepository {

    private val stored = MutableStateFlow(initial)

    override fun observeRecipes(filter: RecipeFilter): Flow<List<Recipe>> = stored
    override fun observeRecipe(id: String): Flow<Recipe?> =
        stored.map { list -> list.firstOrNull { it.id == id } }
    override fun observeRecipeCount(): Flow<Int> = stored.map { it.size }
    override suspend fun upsert(recipe: Recipe): String = recipe.id
    override suspend fun delete(id: String) = Unit
    override suspend fun markCooked(id: String, at: Long) = Unit
    override suspend fun setFavourite(id: String, favourite: Boolean) = Unit
}

class FakeTagRepository(initial: List<Tag> = emptyList()) : TagRepository {

    private val stored = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Tag>> = stored
    override suspend fun findOrCreateUserTag(name: String): Tag =
        Tag(id = "tag-${name.trim().lowercase()}", key = null, name = name.trim(), isBuiltIn = false)
    override suspend fun deleteUserTag(id: String) = Unit
}

/**
 * Seeds a default scaling for [HomeTestData.lastCookedId], so the Continue cooking card has a
 * variant label to show without every test wiring one up by hand.
 *
 * The dashboard never reads a constraint back out of a variant — only [observeForRecipe] and its
 * label — so the payload here is a placeholder rather than real JSON, which keeps this module's
 * test dependencies to what the dashboard actually exercises.
 */
class FakeScaleVariantRepository(initial: List<ScaleVariant>? = null) : ScaleVariantRepository {

    private val stored = MutableStateFlow(
        initial ?: listOf(
            ScaleVariant(
                id = "variant-default",
                recipeId = HomeTestData.lastCookedId,
                label = HomeTestData.LAST_COOKED_VARIANT_LABEL,
                constraintPayload = "",
                isDefault = true,
            ),
        ),
    )

    override fun observeForRecipe(recipeId: String): Flow<List<ScaleVariant>> =
        stored.map { list -> list.filter { it.recipeId == recipeId } }

    override fun readConstraint(variant: ScaleVariant): ScaleConstraint =
        throw UnsupportedOperationException("the dashboard never scales a recipe, only labels it")

    override suspend fun save(
        recipeId: String,
        label: String,
        constraint: ScaleConstraint,
        asDefault: Boolean,
    ): String {
        val id = "variant-${stored.value.size + 1}"
        stored.value = stored.value + ScaleVariant(
            id = id,
            recipeId = recipeId,
            label = label,
            constraintPayload = "",
            isDefault = asDefault,
        )
        return id
    }

    override suspend fun delete(id: String) {
        stored.value = stored.value.filterNot { it.id == id }
    }
}
