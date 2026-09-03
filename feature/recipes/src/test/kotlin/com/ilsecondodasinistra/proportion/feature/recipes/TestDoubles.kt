package com.ilsecondodasinistra.proportion.feature.recipes

import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeFilter
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.BakingAdvisor
import com.ilsecondodasinistra.proportion.core.domain.scale.DefaultRecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.DiscreteAnalyser
import com.ilsecondodasinistra.proportion.core.domain.scale.RecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant
import com.ilsecondodasinistra.proportion.core.transfer.DecodeResult
import com.ilsecondodasinistra.proportion.core.transfer.ImportMode
import com.ilsecondodasinistra.proportion.core.transfer.ImportOutcome
import com.ilsecondodasinistra.proportion.core.transfer.ImportPreview
import com.ilsecondodasinistra.proportion.core.transfer.ProportionCodec
import com.ilsecondodasinistra.proportion.core.transfer.TransferRepository
import kotlinx.serialization.json.Json
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.Tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

object TestData {

    fun ingredient(name: String, unit: MeasureUnit = MeasureUnit.GRAM) = Ingredient(
        id = "ing-${name.lowercase()}",
        key = null,
        name = name,
        normalisedName = name.lowercase(),
        isBuiltIn = false,
        defaultUnit = unit,
    )

    val dessertTag = Tag(id = "tag-dessert", key = "dessert", name = null, isBuiltIn = true)
    val firstCourseTag = Tag(id = "tag-first", key = "first_course", name = null, isBuiltIn = true)
    val userTag = Tag(id = "tag-merenda", key = null, name = "merenda", isBuiltIn = false)

    val flour = ingredient("Farina")
    val eggs = ingredient("Uova", MeasureUnit.EGG)
    val rice = ingredient("Riso")

    val cake = Recipe(
        id = "r-cake",
        title = "Torta di mele",
        servings = 4,
        steps = listOf("Sbatti le uova."),
        ingredients = listOf(
            RecipeIngredient("l-1", flour, 0, 300.0, MeasureUnit.GRAM),
            RecipeIngredient("l-2", eggs, 1, 2.0, MeasureUnit.EGG),
        ),
        tags = listOf(dessertTag),
        updatedAt = 200L,
    )

    val risotto = Recipe(
        id = "r-risotto",
        title = "Risotto allo zafferano",
        servings = 2,
        steps = listOf("Tosta il riso."),
        ingredients = listOf(RecipeIngredient("l-3", rice, 0, 320.0, MeasureUnit.GRAM)),
        tags = listOf(firstCourseTag),
        updatedAt = 100L,
    )
}

/**
 * Applies the same filter semantics as the SQL query: text matches title or ingredient name, tags
 * match any of the selection, ingredients must all be present.
 */
class FakeRecipeRepository(initial: List<Recipe> = emptyList()) : RecipeRepository {

    private val stored = MutableStateFlow(initial)

    /** Every filter the screen asked for, in order — this is how the debounce test observes calls. */
    val requestedFilters = mutableListOf<RecipeFilter>()

    override fun observeRecipes(filter: RecipeFilter): Flow<List<Recipe>> {
        requestedFilters += filter
        return stored.map { recipes -> recipes.filter { it.matches(filter) }.sorted(filter) }
    }

    override fun observeRecipe(id: String): Flow<Recipe?> =
        stored.map { list -> list.firstOrNull { it.id == id } }

    override fun observeRecipeCount(): Flow<Int> = stored.map { it.size }

    override suspend fun upsert(recipe: Recipe): String {
        stored.value = stored.value.filterNot { it.id == recipe.id } + recipe
        return recipe.id
    }

    override suspend fun delete(id: String) {
        stored.value = stored.value.filterNot { it.id == id }
    }

    override suspend fun markCooked(id: String, at: Long) {
        stored.value = stored.value.map {
            if (it.id == id) it.copy(cookCount = it.cookCount + 1, lastCookedAt = at) else it
        }
    }

    override suspend fun setFavourite(id: String, favourite: Boolean) {
        stored.value = stored.value.map { if (it.id == id) it.copy(isFavourite = favourite) else it }
    }

    private fun Recipe.matches(filter: RecipeFilter): Boolean {
        val query = filter.query.trim().lowercase()
        val textMatches = query.isEmpty() ||
            title.lowercase().contains(query) ||
            ingredients.any { it.ingredient.normalisedName.contains(query) } ||
            notes.orEmpty().lowercase().contains(query)

        val tagMatches = filter.tagIds.isEmpty() || tags.any { it.id in filter.tagIds }
        val ingredientMatches = filter.ingredientIds.isEmpty() ||
            filter.ingredientIds.all { wanted -> ingredients.any { it.ingredient.id == wanted } }

        return textMatches && tagMatches && ingredientMatches
    }

    private fun List<Recipe>.sorted(filter: RecipeFilter) = when (filter.sort) {
        com.ilsecondodasinistra.proportion.core.domain.repository.RecipeSort.ALPHABETICAL ->
            sortedBy { it.title.lowercase() }
        com.ilsecondodasinistra.proportion.core.domain.repository.RecipeSort.MOST_COOKED ->
            sortedByDescending { it.cookCount }
        else -> sortedByDescending { it.updatedAt }
    }
}

class FakeIngredientRepository(initial: List<Ingredient> = emptyList()) : IngredientRepository {

    private val stored = MutableStateFlow(initial)
    val densityUpdates = mutableListOf<Triple<String, Double?, Double?>>()

    override fun observeAll(): Flow<List<Ingredient>> = stored

    override fun observeInUse(): Flow<List<Ingredient>> = stored

    override suspend fun findOrCreate(name: String, defaultUnit: MeasureUnit): Ingredient {
        val normalised = name.trim().lowercase()
        stored.value.firstOrNull { it.normalisedName == normalised }?.let { return it }

        val created = Ingredient(
            id = "ing-$normalised",
            key = null,
            name = name.trim(),
            normalisedName = normalised,
            isBuiltIn = false,
            defaultUnit = defaultUnit,
        )
        stored.value = stored.value + created
        return created
    }

    override suspend fun setDensityData(id: String, densityGramsPerMl: Double?, itemWeightGrams: Double?) {
        densityUpdates += Triple(id, densityGramsPerMl, itemWeightGrams)
        stored.value = stored.value.map { ingredient ->
            if (ingredient.id != id) {
                ingredient
            } else {
                ingredient.copy(
                    densityGramsPerMl = densityGramsPerMl ?: ingredient.densityGramsPerMl,
                    itemWeightGrams = itemWeightGrams ?: ingredient.itemWeightGrams,
                )
            }
        }
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
        stored.value = stored.value.filterNot { it.id == id && !it.isBuiltIn }
    }
}

private val variantJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class FakeScaleVariantRepository(initial: List<ScaleVariant> = emptyList()) : ScaleVariantRepository {

    private val stored = MutableStateFlow(initial)

    override fun observeForRecipe(recipeId: String): Flow<List<ScaleVariant>> =
        stored.map { list -> list.filter { it.recipeId == recipeId } }

    override fun readConstraint(variant: ScaleVariant): ScaleConstraint =
        variantJson.decodeFromString(variant.constraintPayload)

    override suspend fun save(
        recipeId: String,
        label: String,
        constraint: ScaleConstraint,
        asDefault: Boolean,
    ): String {
        val id = "variant-${stored.value.size + 1}"
        val cleared = if (asDefault) stored.value.map { it.copy(isDefault = false) } else stored.value
        stored.value = cleared + ScaleVariant(
            id = id,
            recipeId = recipeId,
            label = label,
            constraintPayload = variantJson.encodeToString(constraint),
            isDefault = asDefault,
        )
        return id
    }

    override suspend fun delete(id: String) {
        stored.value = stored.value.filterNot { it.id == id }
    }
}

/**
 * Builds a [ScaleVariant] with a real, decodable payload without going through the repository's
 * `save`, so a test can pin the id and construct a constraint that will not resolve (e.g. a
 * deleted ingredient line) without the repository rejecting it up front.
 */
fun testScaleVariant(
    id: String,
    recipeId: String,
    label: String,
    constraint: ScaleConstraint,
    isDefault: Boolean = false,
): ScaleVariant = ScaleVariant(
    id = id,
    recipeId = recipeId,
    label = label,
    constraintPayload = variantJson.encodeToString(constraint),
    isDefault = isDefault,
)

/** Italian unit names, standing in for the Android resource lookup. */
class TestUnitNamer : UnitNamer {
    override fun shortName(unit: MeasureUnit, qty: Double): String = when (unit) {
        MeasureUnit.GRAM -> "g"
        MeasureUnit.KILOGRAM -> "kg"
        MeasureUnit.EGG -> if (qty == 1.0) "uovo" else "uova"
        MeasureUnit.TO_TASTE -> "q.b."
        else -> unit.name.lowercase()
    }
}

fun testConverter(): UnitConverter = DefaultUnitConverter()

fun testFormatter(): QuantityFormatter =
    QuantityFormatter(testConverter(), TestUnitNamer())

fun testScaler(): RecipeScaler {
    val converter = DefaultUnitConverter()
    val formatter = testFormatter()
    return DefaultRecipeScaler(converter, formatter, DiscreteAnalyser(formatter), BakingAdvisor())
}

/** Exercises the real codec, so a share payload that would not parse fails the test. */
class FakeTransferRepository(private val recipes: List<Recipe>) : TransferRepository {

    override suspend fun exportAll(): String = ProportionCodec.encode(recipes)

    override suspend fun exportRecipe(recipeId: String): String? =
        recipes.firstOrNull { it.id == recipeId }?.let { ProportionCodec.encode(listOf(it)) }

    override suspend fun preview(text: String): ImportPreview =
        when (val decoded = ProportionCodec.decode(text)) {
            is DecodeResult.Failure -> ImportPreview.Invalid(decoded.reason)
            is DecodeResult.Success -> ImportPreview.Ready(decoded.recipes.size, 0)
        }

    override suspend fun import(text: String, mode: ImportMode): ImportOutcome =
        ImportOutcome.Imported(added = 0, skipped = 0, replacedLibrary = false)
}
