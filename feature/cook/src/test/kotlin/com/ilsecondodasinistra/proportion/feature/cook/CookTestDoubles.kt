package com.ilsecondodasinistra.proportion.feature.cook

import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeFilter
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ShoppingRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.BakingAdvisor
import com.ilsecondodasinistra.proportion.core.domain.scale.DefaultRecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.DiscreteAnalyser
import com.ilsecondodasinistra.proportion.core.domain.scale.RecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledLine
import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant
import com.ilsecondodasinistra.proportion.core.model.ShoppingItem
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
import kotlinx.serialization.json.Json
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class TestUnitNamer : UnitNamer {
    override fun shortName(unit: MeasureUnit, qty: Double): String = when (unit) {
        MeasureUnit.GRAM -> "g"
        MeasureUnit.KILOGRAM -> "kg"
        MeasureUnit.EGG -> if (qty == 1.0) "uovo" else "uova"
        MeasureUnit.SACHET -> if (qty == 1.0) "bustina" else "bustine"
        MeasureUnit.TO_TASTE -> "q.b."
        else -> unit.name.lowercase()
    }
}

object CookTestData {

    val ovenTag = Tag(id = "tag-oven", key = "oven", name = null, isBuiltIn = true)

    private fun ingredient(name: String, unit: MeasureUnit, densityGramsPerMl: Double? = null) =
        Ingredient(
            id = "ing-${name.lowercase()}", key = null, name = name, normalisedName = name.lowercase(),
            isBuiltIn = false, defaultUnit = unit, densityGramsPerMl = densityGramsPerMl,
        )

    fun line(
        name: String,
        qty: Double?,
        unit: MeasureUnit,
        position: Int,
        densityGramsPerMl: Double? = null,
    ) = RecipeIngredient(
        id = "line-${name.lowercase()}",
        ingredient = ingredient(name, unit, densityGramsPerMl),
        position = position,
        quantity = qty,
        unit = unit,
    )

    /** Serves 4: 300 g flour (known density, so it can be fixed in millilitres), 2 eggs, salt to taste. */
    val cake = Recipe(
        id = "r-cake",
        title = "Torta di mele",
        servings = 4,
        steps = listOf("Sbatti le uova.", "Inforna a 180 gradi."),
        ingredients = listOf(
            line("Farina", 300.0, MeasureUnit.GRAM, 0, densityGramsPerMl = 0.53),
            line("Uova", 2.0, MeasureUnit.EGG, 1),
            line("Sale", null, MeasureUnit.TO_TASTE, 2),
        ),
        tags = emptyList(),
    )

    val ovenCake = cake.copy(id = "r-oven", tags = listOf(ovenTag))

    /** Serves 2 with 3 eggs, so x1.5 asks for 4.5 eggs. */
    val eggRecipe = Recipe(
        id = "r-eggs",
        title = "Frittata",
        servings = 2,
        steps = listOf("Sbatti."),
        ingredients = listOf(line("Uova", 3.0, MeasureUnit.EGG, 0)),
        tags = emptyList(),
    )

    val jam = Recipe(
        id = "r-jam",
        title = "Marmellata",
        servings = null,
        steps = listOf("Cuoci."),
        ingredients = listOf(line("Zucchero", 500.0, MeasureUnit.GRAM, 0)),
        tags = emptyList(),
    )
}

class FakeRecipeRepository(initial: List<Recipe>) : RecipeRepository {

    private val stored = MutableStateFlow(initial)
    val cookedIds = mutableListOf<String>()

    /** The most recent `markCooked` timestamp per recipe id. */
    val cookedAt = mutableMapOf<String, Long>()

    /** How many times `markCooked` was called in total, across every recipe id. */
    var markCookedCalls: Int = 0
        private set

    override fun observeRecipes(filter: RecipeFilter): Flow<List<Recipe>> = stored
    override fun observeRecipe(id: String): Flow<Recipe?> =
        stored.map { list -> list.firstOrNull { it.id == id } }
    override fun observeRecipeCount(): Flow<Int> = stored.map { it.size }
    override suspend fun upsert(recipe: Recipe): String = recipe.id
    override suspend fun delete(id: String) = Unit
    override suspend fun markCooked(id: String, at: Long) {
        cookedIds += id
        cookedAt[id] = at
        markCookedCalls += 1
    }
    override suspend fun setFavourite(id: String, favourite: Boolean) = Unit
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
            id = "ing-$normalised", key = null, name = name.trim(), normalisedName = normalised,
            isBuiltIn = false, defaultUnit = defaultUnit,
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

class FakeScaleVariantRepository : ScaleVariantRepository {

    private val stored = MutableStateFlow<List<ScaleVariant>>(emptyList())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val savedConstraints = mutableListOf<Pair<String, ScaleConstraint>>()

    override fun observeForRecipe(recipeId: String): Flow<List<ScaleVariant>> =
        stored.map { list -> list.filter { it.recipeId == recipeId } }

    override fun readConstraint(variant: ScaleVariant): ScaleConstraint =
        json.decodeFromString(variant.constraintPayload)

    override suspend fun save(
        recipeId: String,
        label: String,
        constraint: ScaleConstraint,
        asDefault: Boolean,
    ): String {
        savedConstraints += label to constraint
        val id = "variant-${stored.value.size + 1}"
        stored.value = stored.value + ScaleVariant(
            id = id,
            recipeId = recipeId,
            label = label,
            constraintPayload = json.encodeToString(constraint),
            isDefault = asDefault,
        )
        return id
    }

    override suspend fun delete(id: String) = Unit
}

/** One call to [ShoppingRepository.addScaled], recorded verbatim. */
data class AddCall(val lines: List<ScaledLine>, val recipeId: String)

/**
 * Records what was sent, filtered the same way [ShoppingRepository.addScaled] documents it will be
 * filtered (lines with nothing to buy are dropped) — merging is the real repository's job, not this
 * double's, so it is not reproduced here.
 */
class FakeShoppingRepository : ShoppingRepository {

    val added = mutableListOf<AddCall>()

    override fun observeItems(): Flow<List<ShoppingItem>> = MutableStateFlow(emptyList())

    override suspend fun addScaled(lines: List<ScaledLine>, recipeId: String) {
        added += AddCall(lines.filter { it.isAddableToShoppingList() }, recipeId)
    }

    override suspend fun setChecked(id: String, checked: Boolean) = Unit
    override suspend fun clearChecked() = Unit
    override suspend fun clearAll() = Unit
}

fun testFormatter(): QuantityFormatter = QuantityFormatter(DefaultUnitConverter(), TestUnitNamer())

fun testScaler(): RecipeScaler {
    val converter = DefaultUnitConverter()
    val formatter = testFormatter()
    return DefaultRecipeScaler(converter, formatter, DiscreteAnalyser(formatter), BakingAdvisor())
}
